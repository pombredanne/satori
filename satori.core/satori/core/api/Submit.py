# vim:ts=4:sts=4:sw=4:expandtab

from satori.core.cwrapper import ModelWrapper
from satori.core.models import Submit

submit = ModelWrapper(Submit)

submit._fill_module(__name__)

