# vim:ts=4:sts=4:sw=4:expandtab

from django.db import models
from satori.dbev import Events

OATYPES_STRING = 1
OATYPES_BLOB = 2

OATYPES = (
    (OATYPES_STRING, 'String Attribute'),
    (OATYPES_BLOB, 'Blob Attribute'),
)

class OpenAttribute(models.Model):
    """Model. Base for all kinds of open attributes.
    """
    __module__ = "satori.core.models"

    OATYPES_STRING = OATYPES_STRING
    OATYPES_BLOB = OATYPES_BLOB

    object      = models.ForeignKey('Object', related_name='attributes')
    name        = models.CharField(max_length=50)

    oatype       = models.IntegerField(choices=OATYPES)
    string_value = models.TextField(null=True)
    blob         = models.ForeignKey('Blob', null=True)

    @staticmethod
    def get_str(obj, name):
        try:
            return obj.attributes.get(name=name, oatype=OATYPES_STRING).string_value
        except:
            return None

    @staticmethod
    def set_str(obj, name, value):
        try:
            oa = obj.attributes.get(name=name)
        except:
            oa = OpenAttribute(object=obj, name=name)
        oa.type = OATYPES_STRING
        oa.string_value = value
        oa.save()

    def save(self, *args, **kwargs):
        str = self.string_value
        blo = self.blob
        self.string_value = None
        self.blob = None
        if self.oatype == OATYPES_STRING:
        	self.string_value = str
        if self.oatype == OATYPES_BLOB:
        	self.blob = blo
        super(OpenAttribute, self).save(*args, **kwargs)

    class Meta:                                                # pylint: disable-msg=C0111
        unique_together = (('object', 'name'),)

class OpenAttributeEvents(Events):
    model = OpenAttribute
    on_insert = on_update = on_delete = []
