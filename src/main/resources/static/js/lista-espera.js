(function () {
  'use strict';
  var form = document.getElementById('formEspera');
  if (!form) { return; }
  var base = form.dataset.base;

  if (form.dataset.personal === 'true') {
    MG.buscadorPacientes(base, document.getElementById('buscarPaciente'), document.getElementById('listaPacientes'),
      document.getElementById('pacienteId'), document.getElementById('pacienteElegido'));
  }

  var esp = document.getElementById('esperaEspecialidad');
  var med = document.getElementById('esperaMedico');
  esp.addEventListener('change', function () {
    med.replaceChildren();
    var cualquiera = document.createElement('option');
    cualquiera.value = '';
    cualquiera.textContent = 'Cualquier médico de la especialidad';
    med.appendChild(cualquiera);
    if (!esp.value) { return; }
    MG.json(base + '/medicos?especialidadId=' + encodeURIComponent(esp.value)).then(function (lista) {
      lista.forEach(function (m) {
        var o = document.createElement('option');
        o.value = m.id;
        o.textContent = m.texto;
        med.appendChild(o);
      });
    }).catch(function () { /* se mantiene "cualquier médico" */ });
  });
})();
