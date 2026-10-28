/* Utilidades compartidas por las páginas de MediGest. Sin dependencias; el texto se inserta con textContent. */
(function (w) {
  'use strict';
  var MG = {};

  MG.json = function (url) {
    return fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
      .then(function (r) {
        if (r.ok) { return r.json(); }
        return r.json().catch(function () { return {}; }).then(function (p) {
          throw new Error(p.detail || p.title || 'No se pudo cargar la información.');
        });
      });
  };

  MG.fechaISO = function (offsetDias) {
    var d = new Date();
    d.setDate(d.getDate() + offsetDias);
    var p = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
  };

  MG.etiquetaDia = function (fechaISO) {
    return new Date(fechaISO + 'T12:00:00').toLocaleDateString('es-PE',
      { weekday: 'long', day: 'numeric', month: 'long' });
  };

  MG.debounce = function (fn, ms) {
    var t;
    return function () {
      var args = arguments, ctx = this;
      clearTimeout(t);
      t = setTimeout(function () { fn.apply(ctx, args); }, ms);
    };
  };

  MG.mensaje = function (contenedor, texto, clase) {
    contenedor.replaceChildren();
    var p = document.createElement('p');
    p.className = clase || 'text-secondary mb-0';
    p.textContent = texto;
    contenedor.appendChild(p);
  };

  /* Horarios libres de un médico (próximos 30 días) agrupados por día. */
  MG.cargarHorarios = function (base, medicoId, contenedor, alElegir) {
    MG.mensaje(contenedor, 'Buscando horarios…');
    var url = base + '/medicos/' + encodeURIComponent(medicoId) + '/horarios?desde=' + MG.fechaISO(0) +
      '&hasta=' + MG.fechaISO(30);
    return MG.json(url).then(function (dias) {
      var conHorarios = dias.filter(function (d) { return d.horarios && d.horarios.length; });
      if (!conHorarios.length) {
        MG.mensaje(contenedor, 'Este médico no tiene horarios libres en los próximos 30 días.', 'text-warning mb-0');
        return;
      }
      contenedor.replaceChildren();
      var activo = null;
      conHorarios.forEach(function (dia) {
        var titulo = document.createElement('div');
        titulo.className = 'fw-semibold mt-3 mb-1 mg-dia-titulo';
        titulo.textContent = MG.etiquetaDia(dia.fecha);
        contenedor.appendChild(titulo);
        var fila = document.createElement('div');
        fila.className = 'd-flex flex-wrap gap-2';
        dia.horarios.forEach(function (h) {
          var b = document.createElement('button');
          b.type = 'button';
          b.className = 'btn btn-outline-primary btn-sm mg-slot';
          b.textContent = h.inicio.substring(11, 16);
          b.addEventListener('click', function () {
            if (activo) { activo.classList.remove('active'); }
            b.classList.add('active');
            activo = b;
            alElegir({ inicio: h.inicio, fin: h.fin, fecha: dia.fecha, hora: b.textContent });
          });
          fila.appendChild(b);
        });
        contenedor.appendChild(fila);
      });
    }).catch(function (e) { MG.mensaje(contenedor, e.message, 'text-danger mb-0'); });
  };

  /* Buscador de pacientes (solo personal): rellena un campo oculto con el id elegido. */
  MG.buscadorPacientes = function (base, input, lista, campoId, etiqueta, alElegir) {
    var buscar = MG.debounce(function () {
      var q = input.value.trim();
      lista.replaceChildren();
      if (q.length < 2) { return; }
      MG.json(base + '/pacientes?q=' + encodeURIComponent(q)).then(function (res) {
        if (!res.length) {
          var vacio = document.createElement('div');
          vacio.className = 'list-group-item text-secondary';
          vacio.textContent = 'Sin resultados.';
          lista.appendChild(vacio);
          return;
        }
        res.forEach(function (p) {
          var item = document.createElement('button');
          item.type = 'button';
          item.className = 'list-group-item list-group-item-action';
          item.textContent = p.texto;
          item.addEventListener('click', function () {
            campoId.value = p.id;
            etiqueta.textContent = 'Paciente: ' + p.texto;
            input.value = '';
            lista.replaceChildren();
            if (alElegir) { alElegir(p); }
          });
          lista.appendChild(item);
        });
      }).catch(function () { /* se puede reintentar escribiendo de nuevo */ });
    }, 300);
    input.addEventListener('input', buscar);
  };

  /* Formularios con data-confirmar="texto" piden confirmación antes de enviarse. */
  document.addEventListener('submit', function (e) {
    var texto = e.target && e.target.dataset ? e.target.dataset.confirmar : null;
    if (texto && !window.confirm(texto)) { e.preventDefault(); }
  });

  w.MG = MG;
})(window);
