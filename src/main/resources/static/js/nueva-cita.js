(function () {
  'use strict';
  var form = document.getElementById('formReserva');
  if (!form) { return; }

  var base = form.dataset.base;
  var esPersonal = form.dataset.personal === 'true';
  var selEsp = document.getElementById('especialidad');
  var panelMedicos = document.getElementById('medicos');
  var panelHorarios = document.getElementById('horarios');
  var resumen = document.getElementById('resumen');
  var btn = document.getElementById('btnReservar');
  var campoMedico = document.getElementById('medicoId');
  var campoEsp = document.getElementById('especialidadId');
  var campoInicio = document.getElementById('inicio');
  var campoPaciente = document.getElementById('pacienteId');

  function validar() {
    var ok = campoInicio.value && campoMedico.value && (!esPersonal || campoPaciente.value);
    btn.disabled = !ok;
  }

  /* --- buscador de pacientes (personal) --- */
  if (esPersonal) {
    MG.buscadorPacientes(base, document.getElementById('buscarPaciente'), document.getElementById('listaPacientes'),
      campoPaciente, document.getElementById('pacienteElegido'), validar);
  }

  /* --- especialidad → médicos con horarios libres --- */
  selEsp.addEventListener('change', function () {
    campoMedico.value = '';
    campoInicio.value = '';
    campoEsp.value = selEsp.value;
    resumen.textContent = '';
    panelHorarios.replaceChildren();
    panelMedicos.replaceChildren();
    validar();
    if (!selEsp.value) { return; }
    MG.mensaje(panelMedicos, 'Buscando médicos disponibles…');
    var url = base + '/medicos-disponibles?especialidadId=' + encodeURIComponent(selEsp.value) +
      '&desde=' + MG.fechaISO(0) + '&hasta=' + MG.fechaISO(13);
    MG.json(url).then(function (medicos) {
      if (!medicos.length) {
        MG.mensaje(panelMedicos, 'No hay médicos con horarios libres en los próximos 14 días. ' +
          'Puede anotarse en la lista de espera.', 'text-warning mb-0');
        return;
      }
      panelMedicos.replaceChildren();
      var fila = document.createElement('div');
      fila.className = 'row g-2';
      medicos.forEach(function (m) {
        var col = document.createElement('div');
        col.className = 'col-md-6';
        var card = document.createElement('div');
        card.className = 'card mg-medico';
        card.tabIndex = 0;
        var cuerpo = document.createElement('div');
        cuerpo.className = 'card-body py-2';
        var nombre = document.createElement('div');
        nombre.className = 'fw-semibold';
        nombre.textContent = m.medico;
        var detalle = document.createElement('div');
        detalle.className = 'small text-secondary';
        var prox = m.proximoHorario ? m.proximoHorario.inicio : null;
        detalle.textContent = (m.consultorio || 'Consultorio por asignar') + ' · ' + m.duracionConsultaMin +
          ' min · ' + m.totalHorarios + ' horarios' +
          (prox ? ' · próximo ' + prox.substring(8, 10) + '/' + prox.substring(5, 7) + ' ' + prox.substring(11, 16) : '');
        cuerpo.appendChild(nombre);
        cuerpo.appendChild(detalle);
        card.appendChild(cuerpo);
        var elegir = function () {
          Array.prototype.forEach.call(panelMedicos.querySelectorAll('.mg-medico'), function (c) { c.classList.remove('activo'); });
          card.classList.add('activo');
          campoMedico.value = m.medicoId;
          campoInicio.value = '';
          resumen.textContent = '';
          validar();
          MG.cargarHorarios(base, m.medicoId, panelHorarios, function (h) {
            campoInicio.value = h.inicio;
            resumen.textContent = 'Horario elegido: ' + MG.etiquetaDia(h.fecha) + ' a las ' + h.hora + ' con ' + m.medico + '.';
            validar();
          });
        };
        card.addEventListener('click', elegir);
        card.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); elegir(); } });
        col.appendChild(card);
        fila.appendChild(col);
      });
      panelMedicos.appendChild(fila);
    }).catch(function (e) { MG.mensaje(panelMedicos, e.message, 'text-danger mb-0'); });
  });

  form.addEventListener('submit', function () { btn.disabled = true; });
  validar();
})();
