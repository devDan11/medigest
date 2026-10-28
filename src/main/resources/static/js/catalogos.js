/* Modal único de alta/edición para los catálogos: los datos de la fila llegan en atributos data-*. */
(function () {
  'use strict';
  var modal = document.getElementById('modalCatalogo');
  if (!modal) { return; }

  modal.addEventListener('show.bs.modal', function (e) {
    var origen = e.relatedTarget;
    var datos = origen && origen.dataset && origen.dataset.id ? origen.dataset : null;
    var campoId = document.getElementById('campoId');
    campoId.value = datos ? datos.id : '';
    campoId.disabled = !datos; // sin id el servidor crea; con id actualiza

    Array.prototype.forEach.call(modal.querySelectorAll('[id^="campo"]'), function (campo) {
      if (campo === campoId) { return; }
      var clave = campo.id.charAt(5).toLowerCase() + campo.id.slice(6); // campoNombre -> nombre
      if (datos) {
        campo.value = datos[clave] != null ? datos[clave] : '';
      } else if (campo.type !== 'number') {
        campo.value = '';
      }
    });
  });
})();
