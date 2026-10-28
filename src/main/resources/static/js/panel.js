(function () {
  'use strict';
  if (typeof Chart === 'undefined') { return; }

  var colores = ['#e0a800', '#0d6efd', '#0dcaf0', '#212529', '#198754', '#dc3545', '#6c757d'];

  function datos(canvas) {
    return {
      etiquetas: (canvas.dataset.etiquetas || '').split('|'),
      valores: (canvas.dataset.valores || '').split(',').map(Number)
    };
  }

  var estados = document.getElementById('graficoEstados');
  if (estados) {
    var d = datos(estados);
    var hayDatos = d.valores.some(function (v) { return v > 0; });
    new Chart(estados, {
      type: 'doughnut',
      data: {
        labels: d.etiquetas,
        datasets: [{ data: hayDatos ? d.valores : d.valores.map(function () { return 0; }), backgroundColor: colores }]
      },
      options: { maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
    });
  }

  var dias = document.getElementById('graficoDias');
  if (dias) {
    var s = datos(dias);
    new Chart(dias, {
      type: 'bar',
      data: { labels: s.etiquetas, datasets: [{ label: 'Citas', data: s.valores, backgroundColor: '#0d6efd', borderRadius: 4 }] },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }
})();
