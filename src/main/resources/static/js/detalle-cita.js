(function () {
  'use strict';
  var modal = document.getElementById('modalReprogramar');
  if (!modal) { return; }

  var base = modal.dataset.base;
  var medicoId = modal.dataset.medicoId;
  var contenedor = document.getElementById('horariosReprogramar');
  var campo = document.getElementById('nuevoInicio');
  var boton = document.getElementById('btnReprogramar');
  var resumen = document.getElementById('resumenReprogramar');
  var cargado = false;

  modal.addEventListener('show.bs.modal', function () {
    if (cargado) { return; }
    cargado = true;
    MG.cargarHorarios(base, medicoId, contenedor, function (h) {
      campo.value = h.inicio;
      resumen.textContent = 'Nuevo horario: ' + MG.etiquetaDia(h.fecha) + ' a las ' + h.hora + '.';
      boton.disabled = false;
    });
  });
})();
