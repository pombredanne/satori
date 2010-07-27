from thrift.transport.TSocket import TSocket
from satori.ars.thrift import ThriftReader, ThriftClient, ThreadedThriftClient
from satori.ars import ars2py
import sys

def transport_factory():
    return TSocket(host='satori.tcs.uj.edu.pl', port=38889)

print 'Bootstrapping client...'

_bootstrap_client = ThriftClient(transport_factory())
_bootstrap_client.start(bootstrap=True)
_bootstrap_client.stop()

_client = ThreadedThriftClient(_bootstrap_client.contracts, transport_factory)

_module = sys.modules[__name__]

_classes = ars2py.generate_classes(_client)

for name, value in _classes.iteritems():
    setattr(_module, name, value)

setattr(_module, '__all__', _classes.keys() + ['set_token', 'get_token', 'unset_token'])

print 'Client bootstrapped.'

def set_token(token):
    ars2py.token_container.set_token(token)

def get_token():
    return ars2py.token_container.get_token()

def unset_token():
    ars2py.token_container.unset_token()

def start_console():
    import code
    import readline
    console = code.InteractiveConsole()
    console.runcode('import satori.client.common as satori_classes')
    print
    print 'satori.client.common is imported as satori_classes'
    print
    console.interact()

