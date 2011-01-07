# vim:ts=4:sts=4:sw=4:expandtab

from django.db import models

from satori.core.dbev import Events

class OpenAttribute(models.Model):
    """Model. Base for all kinds of open attributes.
    """
    object   = models.ForeignKey('Entity', related_name='attributes')
    name     = models.CharField(max_length=50)
    is_blob  = models.BooleanField()
    value    = models.TextField()
    filename = models.CharField(max_length=50)

    class Meta:                                                # pylint: disable-msg=C0111
        unique_together = (('object', 'name'),)

    def save(self, *args, **kwargs):
        if not self.is_blob:
            self.filename = ''
        super(OpenAttribute, self).save(*args, **kwargs)

class OpenAttributeEvents(Events):
    model = OpenAttribute
    on_insert = on_update = on_delete = []
