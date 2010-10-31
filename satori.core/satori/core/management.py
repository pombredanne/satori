# vim:ts=4:sts=4:sw=4:expandtab

from django.db.models.signals import post_syncdb

def create_admin(app, created_models, verbosity, **kwargs):
    
    import satori.core.models
    from satori.core.models import Entity, User, Privilege

    if (app != satori.core.models) or (Entity not in created_models):
        return

    from django.conf import settings
    from satori.core.dbev.install import install_dbev_sql, install_rights_sql
    from satori.core.export import token_container
    from satori.core.models import Security, Privilege
    from satori.core.sec import Token

    print 'Installing DBEV'

    sql = install_dbev_sql()
    from django.db import connection, transaction
    cursor = connection.cursor()
    for query in sql:
    	cursor.execute(query)

    print 'Creating superuser'

    token_container.token = Token('')
    User.register(login=settings.ADMIN_NAME, name='Super Admin', password=settings.ADMIN_PASSWORD)
    admin = User.objects.get(login='admin')
    Privilege.global_grant(admin, 'ADMIN')

    print 'Installing DBEV rights'

    sql = install_rights_sql()
    from django.db import connection, transaction
    cursor = connection.cursor()
    for query in sql:
    	cursor.execute(query)


post_syncdb.connect(create_admin)
