# vim:ts=4:sts=4:sw=4:expandtab
from setuptools import setup, find_packages

setup(name='satori.tools',
    packages=find_packages(),
    namespace_packages=[
        'satori',
    ],
    install_requires=[
        'setuptools',
        'satori.client.common',
    ],
    entry_points='''
        [console_scripts]
        satori.athina_import = satori.tools.athina:athina_import
        satori.athina_import_testsuite = satori.tools.athina:athina_import_testsuite
        satori.athina_import_problem = satori.tools.athina:athina_import_problem
        satori.console = satori.tools.console:main
        satori.default_judges = satori.tools.judges:default_judges
        satori.team = satori.tools.teams:uzi_team
    ''',
)
