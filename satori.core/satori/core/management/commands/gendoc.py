# vim:ts=4:sts=4:sw=4:expandtab

from   django.core.management.base import BaseCommand, CommandError
import os
from   sphinx.application import Sphinx
import sphinx.ext.autodoc
import shutil
import sys

from satori.ars.model import *

from docutils import nodes
from sphinx import addnodes
from sphinx.builders import BUILTIN_BUILDERS
from sphinx.builders.html import PickleHTMLBuilder
from sphinx.domains import Domain, ObjType, BUILTIN_DOMAINS 
from sphinx.directives import ObjectDescription
from sphinx.roles import XRefRole
from sphinx.util.nodes import make_refnode


atomic_type_names = {
    ArsBinary:  'binary',
    ArsBoolean: 'bool',
    ArsInt8:    'byte',
    ArsInt16:   'i16',
    ArsInt32:   'i32',
    ArsInt64:   'i64',
    ArsFloat:   'double',
    ArsString:  'string',
    ArsVoid:    'void',
}


def gen_type_node(node, type):
    if isinstance(type, ArsAtomicType):
        node += nodes.literal(atomic_type_names[type], atomic_type_names[type])
    elif isinstance(type, ArsList):
        node += nodes.literal('list', 'list')
        node += nodes.Text('<')
        gen_type_node(node, type.element_type)
        node += nodes.Text('>')
    elif isinstance(type, ArsMap):
        node += nodes.literal('map', 'map')
        node += nodes.Text('<')
        gen_type_node(node, type.key_type)
        node += nodes.Text(',')
        gen_type_node(node, type.value_type)
        node += nodes.Text('>')
    elif isinstance(type, ArsNamedType):
        refnode = addnodes.pending_xref('', refdomain='ars', reftype='type', reftarget=type.name, modname=None, classname=None)
        refnode += nodes.literal(type.name, type.name)
        node += refnode
    else:
        raise RuntimeError('Cannot reference type: {0}'.format(str(type)))


class ArsTypeDirective(ObjectDescription):
    def add_target_and_index(self, sigobj, sig, signode):
        name = self.type.name

        signode['names'].append(name)
        signode['ids'].append(name)
        self.state.document.note_explicit_target(signode)
        self.env.domaindata['ars']['types'].setdefault(name, self.env.docname)

    def before_content(self):
        self.env.temp_data['ars:typename'] = self.type.name
    
    def after_content(self):
        del self.env.temp_data['ars:typename']

    def handle_signature(self, sig, signode):
        self.type = ars_interface.types[sig.strip()]

        if isinstance(self.type, ArsException):
            signode += nodes.literal('exception', 'exception')
            signode += nodes.Text(' ')
            signode += addnodes.desc_name(self.type.name, self.type.name)
        elif isinstance(self.type, ArsStructure):
            signode += nodes.literal('structure', 'structure')
            signode += nodes.Text(' ')
            signode += addnodes.desc_name(self.type.name, self.type.name)
        elif isinstance(self.type, ArsTypeAlias):
            signode += nodes.literal('typedef', 'typedef')
            signode += nodes.Text(' ')
            signode += addnodes.desc_name(self.type.name, self.type.name)
            signode += nodes.Text(' ')
            gen_type_node(signode, self.type.target_type)
        else:
            raise RuntimeError('Cannot generate type definition: {0}'.format(str(self.type)))

        return self.type.name


class ArsFieldDirective(ObjectDescription):
    def add_target_and_index(self, sigobj, sig, signode):
        name = self.type.name + '.' + self.field.name

        signode['names'].append(name)
        signode['ids'].append(name)
        self.state.document.note_explicit_target(signode)
        self.env.domaindata['ars']['fields'].setdefault(name, self.env.docname)

    def handle_signature(self, sig, signode):
        sig = sig.strip()

        parent = self.env.temp_data.get('ars:typename', None)

        if not parent:
            in_parent = False

            split = sig.split('.', 1)

            if len(split) == 2:
                parent = split[0]
                name = split[1]
            else:
                raise RuntimeError('Parent not found')
        else:
            in_parent = True
            name = sig

        self.type = ars_interface.types[parent]
        self.field = self.type.fields[name]
        
        gen_type_node(signode, self.field.type)
        signode += nodes.Text(' ')
        if not in_parent:
            signode += addnodes.addname(self.type.name, self.type.name)
            signode += nodes.Text('.')
        signode += addnodes.desc_name(self.field.name, self.field.name)
        
        return self.type.name + '.' + self.field.name


class ArsServiceDirective(ObjectDescription):
    def add_target_and_index(self, sigobj, sig, signode):
        name = self.service.name

        signode['names'].append(name)
        signode['ids'].append(name)
        self.state.document.note_explicit_target(signode)
        self.env.domaindata['ars']['services'].setdefault(name, self.env.docname)

    def before_content(self):
        self.env.temp_data['ars:servicename'] = self.service.name
    
    def after_content(self):
        del self.env.temp_data['ars:servicename']

    def handle_signature(self, sig, signode):
        self.service = ars_interface.services[sig.strip()]

        signode += nodes.literal('service', 'service')
        signode += nodes.Text(' ')
        signode += addnodes.desc_name(self.service.name, self.service.name)

        return self.service.name


class ArsAttributeGroupDirective(ObjectDescription):
    def add_target_and_index(self, sigobj, sig, signode):
        name = self.service.name + '.' + self.name

        signode['names'].append(name)
        signode['ids'].append(name)
        self.state.document.note_explicit_target(signode)
        self.env.domaindata['ars']['attributegroups'].setdefault(name, self.env.docname)

    def handle_signature(self, sig, signode):
        sig = sig.strip()

        parent = self.env.temp_data.get('ars:servicename', None)

        if not parent:
            in_parent = False

            split = sig.split('.', 1)

            if len(split) == 2:
                parent = split[0]
                name = split[1]
            else:
                raise RuntimeError('Parent not found')
        else:
            in_parent = True
            name = sig

        self.service = ars_interface.services[parent]
        self.name = name
        
        signode += addnodes.desc_name(self.name, self.name)
        
        return self.service.name + '.' + self.name


class ArsProcedureDirective(ObjectDescription):
    option_spec = {
        'skipargs': int,
    }
    
    def add_target_and_index(self, sigobj, sig, signode):
        name = self.service.name + '.' + self.procedure_name

        signode['names'].append(name)
        signode['ids'].append(name)
        self.state.document.note_explicit_target(signode)
        self.env.domaindata['ars']['procedures'].setdefault(name, self.env.docname)

    def handle_signature(self, sig, signode):
        sig = sig.strip()

        parent = self.env.temp_data.get('ars:servicename', None)

        if not parent:
            in_parent = False

            split = sig.split('.', 1)

            if len(split) == 2:
                parent = split[0]
                name = split[1]
            else:
                raise RuntimeError('Parent not found')
        else:
            in_parent = True
            name = sig

        self.service = ars_interface.services[parent]
        self.procedure_name = name

        base = self.service
        self.procedure = None
        while base is not None:
            if base.name + '_' + name in base.procedures:
                self.procedure = base.procedures[base.name + '_' + name]
                break
            base = base.base

        if not self.procedure:
            raise RuntimeError('Procedure {0} not found in {1} or base services'.format(name, parent))

        skipargs = self.options.get('skipargs', 0)
        
        gen_type_node(signode, self.procedure.return_type)
        signode += nodes.Text(' ')

        if not in_parent:
            signode += addnodes.desc_addname(self.service.name, self.service.name)
            signode += nodes.Text('.')
        signode += addnodes.desc_name(self.procedure_name, self.procedure_name)

        paramlist_node = addnodes.desc_parameterlist()
        for param in list(self.procedure.parameters)[skipargs:]:
            param_node = addnodes.desc_parameter('', '', noemph=True)
            gen_type_node(param_node, param.type)
            param_node += nodes.Text(' ')
            param_node += nodes.emphasis(param.name, param.name)
            paramlist_node += param_node
        signode += paramlist_node

        if len(self.procedure.exception_types) > len(global_exception_types):
            signode += nodes.Text(' ')
            signode += nodes.literal('throws', 'throws')
            signode += nodes.Text(' (')
            first = True
            for exception in list(self.procedure.exception_types)[len(global_exception_types):]:
                if first:
                    first = False
                else:
                    signode += nodes.Text(', ')
                gen_type_node(signode, exception)
            signode += nodes.Text(')')

        return self.service.name + '.' + self.procedure_name


class ArsDomain(Domain):
    name = 'ars'
    label = 'ARS'
    object_types = {
        'type': ObjType('type', 'type'),
        'field': ObjType('field', 'field'),
        'service': ObjType('service', 'service'),
        'attributegroup': ObjType('attributegroup', 'attributegroup'),
        'procedure': ObjType('procedure', 'procedure'),
    }
    directives = {
        'type': ArsTypeDirective,
        'field': ArsFieldDirective,
        'service': ArsServiceDirective,
        'attributegroup': ArsAttributeGroupDirective,
        'procedure': ArsProcedureDirective,
    }
    roles = {
        'type': XRefRole(),
        'field': XRefRole(),
        'service': XRefRole(),
        'attributegroup': XRefRole(),
        'procedure': XRefRole(),
    }
    initial_data = {
        'types': {},
        'fields': {},
        'services': {},
        'attributegroups': {},
        'procedures': {},
    }

    def resolve_xref(self, env, fromdocname, builder, typ, target, node, contnode):
        try:
            docname = self.data[typ + 's'][target]
        except KeyError:
            return

        return make_refnode(builder, fromdocname, docname, target, contnode, target)


BUILTIN_DOMAINS['ars'] = ArsDomain

class GlobalTocPickleHTMLBuilder(PickleHTMLBuilder):
    def get_doc_context(self, docname, body, metatags):
        res = super(GlobalTocPickleHTMLBuilder, self).get_doc_context(docname, body, metatags)
        res['toc'] = self.render_partial(self.env.get_toctree_for(docname, self, True))['fragment']
        return res

BUILTIN_BUILDERS['global_pickle'] = GlobalTocPickleHTMLBuilder

def prepare_doc(obj, indent):
    doc = obj.__dict__.get('__doc__', None)

    if not doc:
        return ''

    return '\n'.join(' ' * indent + docline for docline in doc.split('\n'))


def T(text):
    lines = text.split('\n')

    if lines:
        del lines[0]

    if not lines:
        return ''

    counter = 0
    while (len(lines[0]) > counter) and (lines[0][counter] == ' '):
        counter += 1

    return '\n'.join(line[counter:] for line in lines[1:]) + '\n'


def generate_type(f, type_name):
    type = ars_interface.types[type_name]

    f.write(T("""
        .
        {0}
        {1}
        .. ars:type:: {0}
        
        {2}
        """).format(type_name, '-' * len(type_name), prepare_doc(type, 2)))

    if not isinstance(type, ArsStructure):
        return

    f.write(T("""
        .
          Instance attributes:
        """))

    for field in type.fields:
        f.write(T("""
            .
                .. ars:field:: {0}
            
            {1}
            """).format(field.name, prepare_doc(field, 6)))


def generate_index(f, service_names):
    f.write(T("""
        .
        Satori API documentation
        ========================
        
        .. toctree::
          :hidden:
        
          types
          exceptions
          oa

          ------ <self>
        """))

    for service_name in service_names:
        f.write(T("""
            .
              service_{0}
            """).format(service_name))

    f.close()


def generate_exceptions(f, exception_names):
    f.write(T("""
        .
        Exception types
        ===============
        """))

    for exception_name in exception_names:
        generate_type(f, exception_name)

    f.close()


def generate_types(f, type_names):
    f.write(T("""
        .
        Simple types
        ============
        """))

    for type_name in type_names:
        generate_type(f, type_name)

    f.close()


def generate_oa(f):
    f.write(T("""
        .
        Open attributes
        ===============
        Every class can have defined several open attribute groups that are identified by name. 
        Every open attribute group behaves like a list of (name, value) pairs, where name is a string
        and value can be a string or a blob. If value is a blob, it can hold filename together with the data.

        There are two ways to access open attributes: Thrift functions and blob server (using HTTP).

        Thrift API
        ----------
        Every attribute group defines a set of instance methods for class instances.
        Below are functions defined by attribute group ``oa`` for the :cpp:class:`Entity` class.
        Other attribute groups define similar functions, with ``oa`` changed to the group name
        and they may require different permissions instead of MANAGE.
        """))

    for procedure in ars_interface.services['Entity'].procedures:
        if procedure.name.startswith('Entity_oa_'):
            procedure_name = procedure.name.split('_', 1)[1]

            f.write(T("""
                .
                  .. ars:procedure:: Entity.{0}
                    :skipargs: 2

                {1}
                """).format(procedure_name, prepare_doc(procedure, 4)))

    f.write(T("""
        .
        Blob server
        -----------
        TBD
        """))

    f.close()


def generate_service(f, service_name, additional_types, additional_exceptions):
    f.write(T("""
        .
        {0}
        {1}
        """).format(service_name, '=' * len(service_name)))

    if service_name + 'Id' in ars_interface.types:
        generate_type(f, service_name + 'Id')

    if service_name + 'Struct' in ars_interface.types:
        generate_type(f, service_name + 'Struct')

    for type_name in additional_types:
        generate_type(f, type_name)

    for type_name in additional_exceptions:
        generate_type(f, type_name)

    service = ars_interface.services[service_name]

    methods = ({}, {})

    methods_so_far = (set(), set())

    base = service
    while base is not None:
        for method in base.procedures:
            method_name = method.name.split('_', 1)[1]

            if method.__dict__.get('__doc__', '').startswith('Attribute group:'):
                continue

            if (len(method.parameters) >= 2) and (method.parameters[1].name == '_self'):
                method_type = 1
            else:
                method_type = 0

            if method_name not in methods_so_far[method_type]:
                methods[method_type].setdefault(base, {})[method_name] = method
                methods_so_far[method_type].add(method_name)

        base = base.base

    f.write(T("""
        .
        {0}
        {1}
        .. ars:service:: {0}
        
        {2}
        """).format(service_name, '-' * len(service_name), prepare_doc(service, 2)))

    for method_type in (0, 1):
        if methods[method_type]:
            f.write(T("""
                .
                  {0} methods:

                """).format(['Static', 'Instance'][method_type]))

            base = service
            while base is not None:
                if base in methods[method_type]:
                    if base != service:
                        f.write(T("""
                            .
                                Inherited from :ars:service:`{0}`:
                            """).format(base.name))
                        add = 2
                    else:
                        add = 0

                    for (method_name, method) in sorted(methods[method_type][base].items()):
                        f.write(T("""
                            .
                                {2}.. ars:procedure:: {0}
                                {2}  :skipargs: {3}

                            {1}
                            """).format(method_name, prepare_doc(method, 6 + add), ' ' * add, 1 + method_type))

                base = base.base

    f.close()


class Command(BaseCommand):
    help = 'Generates Thrift API documentation.'
    args = 'destdir'

    def handle(self, *args, **options):
        if len(args) != 1:
            raise CommandError('Command accepts exactly one argument')

        destdir = args[0]
        srcdir = os.path.join(destdir, '_input')

        global ars_interface
        from satori.core.api import ars_interface
        global global_exception_types
        from satori.core.export import global_exception_types

        if os.path.exists(destdir):
            shutil.rmtree(destdir)

        os.makedirs(os.path.join(srcdir))

        type_usage = {}

        def process_type(type_, service):
            if isinstance(type_, ArsNamedType):
                type_usage.setdefault(type_.name, set()).add(service.name)

            if isinstance(type_, ArsTypeAlias):
                process_type(type_.target_type, service)
            elif isinstance(type_, ArsList):
                process_type(type_.element_type, service)
            elif isinstance(type_, ArsSet):
                process_type(type_.element_type, service)
            elif isinstance(type_, ArsMap):
                process_type(type_.key_type, service)
                process_type(type_.value_type, service)
            elif isinstance(type_, ArsStructure):
                for field in type_.fields:
                    process_type(field.type, service)

        for service in ars_interface.services:
            for procedure in service.procedures:
                process_type(procedure.return_type, service)
                for parameter in procedure.parameters:
                    process_type(parameter.type, service)
                for exception_type in procedure.exception_types:
                    process_type(exception_type, service)

        service_names = sorted(ars_interface.services.names)
        type_names = []
        exception_names = []

        additional_types = {}
        additional_exceptions = {}

        for service_name in service_names:
            additional_types[service_name] = []
            additional_exceptions[service_name] = []

        for type_name in sorted(ars_interface.types.names):
            if type_name.endswith('Id') and (type_name[:-2] in service_names):
                continue
            if type_name.endswith('Struct') and (type_name[:-6] in service_names):
                continue
            if len(type_usage[type_name]) == 1:
                if isinstance(ars_interface.types[type_name], ArsException):
                    additional_exceptions[type_usage[type_name].pop()].append(type_name)
                else:
                    additional_types[type_usage[type_name].pop()].append(type_name)
            else:
                if isinstance(ars_interface.types[type_name], ArsException):
                    exception_names.append(type_name)
                else:
                    type_names.append(type_name)

        generate_index(open(os.path.join(srcdir, 'index.rst'), 'w'), service_names)
        generate_types(open(os.path.join(srcdir, 'types.rst'), 'w'), type_names)
        generate_exceptions(open(os.path.join(srcdir, 'exceptions.rst'), 'w'), exception_names)
        generate_oa(open(os.path.join(srcdir, 'oa.rst'), 'w'))

        for service_name in service_names:
            generate_service(open(os.path.join(srcdir, 'service_{0}.rst'.format(service_name)), 'w'), service_name, additional_types[service_name], additional_exceptions[service_name])
        
        conf = {
            'project': 'Satori API',
            'version': '1',
            'release': '1',
            'master_doc': 'index',
            'html_sidebars': {
                '**': ['globaltoc.html', 'searchbox.html'],
            },
        }

        app = Sphinx(srcdir, None, destdir, os.path.join(destdir, '.doctrees'), 'global_pickle',
                     conf, sys.stdout, sys.stderr, True, False, [])

        app.build(True, [])

