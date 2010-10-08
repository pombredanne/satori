# vim:ts=4:sts=4:sw=4:expandtab

from django.db import models
from satori.dbev import Events
from satori.core.models._Entity import Entity

class Message(Entity):
    """Model. Description of a text message.
    """
    __module__ = "satori.core.models"
    parent_object = models.OneToOneField(Entity, parent_link=True, related_name='cast_message')

    topic       = models.CharField(max_length=50, unique=True)
    content     = models.TextField(blank=True, default="")
    time        = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.topic

class MessageEvents(Events):
    model = Message
    on_insert = on_update = ['topic', 'time']
    on_delete = []

