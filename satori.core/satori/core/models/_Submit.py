from django.db import models
from satori.dbev import events
from satori.ars import wrapper
from satori.core import cwrapper
from satori.core.models._Object import Object

class Submit(Object):
    """Model. Single problem solution (within or outside of a Contest).
    """
    __module__ = "satori.core.models"
    parent_object = models.OneToOneField(Object, parent_link=True, related_name='cast_submit')

    owner       = models.ForeignKey('Contestant', null=True)
    problem     = models.ForeignKey('ProblemMapping', null=True)
    time        = models.DateTimeField(auto_now_add=True)
    shortstatus = models.CharField(max_length = 64)
    longstatus  = models.CharField(max_length = 1024)

class SubmitEvents(events.Events):
    model = Submit
    on_insert = on_update = ['owner', 'problem']
    on_delete = []

class SubmitWrapper(wrapper.WrapperClass):
    submit = cwrapper.ModelWrapper(Submit)

