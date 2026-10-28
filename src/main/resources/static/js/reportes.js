(function () {
  'use strict';

  var imprimir = document.querySelector('[data-imprimir]');
  if (imprimir) {
    imprimir.addEventListener('click', function () { window.print(); });
  }
  if (typeof Chart === 'undefined') { return; }

  var colores = ['#0d6efd', '#e0a800', '#198754', '#dc3545', '#0dcaf0', '#6c757d', '#212529'];

  function datos(canvas) {
    var etiquetas = canvas.dataset.etiquetas ? canvas.dataset.etiquetas.split('|') : [];
    var valores = canvas.dataset.valores ? canvas.dataset.valores.split(',').map(Number) : [];
    return { etiquetas: etiquetas, valores: valores };
  }

  var estados = document.getElementById('graficoEstados');
  if (estados) {
    var e = datos(estados);
    new Chart(estados, {
      type: 'doughnut',
      data: { labels: e.etiquetas, datasets: [{ data: e.valores, backgroundColor: colores.concat(colores) }] },
      options: { maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
    });
  }

  var dias = document.getElementById('graficoDias');
  if (dias) {
    var d = datos(dias);
    new Chart(dias, {
      type: 'line',
      data: { labels: d.etiquetas, datasets: [{ label: 'Citas', data: d.valores, borderColor: '#0d6efd', backgroundColor: 'rgba(13,110,253,.15)', fill: true, tension: .25 }] },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }

  var esp = document.getElementById('graficoEspecialidades');
  if (esp) {
    var s = datos(esp);
    new Chart(esp, {
      type: 'bar',
      data: { labels: s.etiquetas, datasets: [{ label: 'Citas', data: s.valores, backgroundColor: '#198754', borderRadius: 4 }] },
      options: {
        indexAxis: 'y',
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }
})();
