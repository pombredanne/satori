from django.db import models
from satori.dbev import events
from satori.ars import django_
from satori.core.models._Object import Object
from satori.core.models._Message import Message

class MessageGlobal(Message):
    """Model. Description of a text message - main screen msg.
    """
    __module__ = "satori.core.models"
    mainscreenonly = models.BooleanField()
    
    def __unicode__(self):
        return self.topic+" (Global)"

