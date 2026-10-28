(function () {
  'use strict';
  var el = document.getElementById('calendario');
  if (!el || typeof FullCalendar === 'undefined') { return; }

  /* Las citas llegan en hora local de la clínica, sin desplazamiento; con zona "UTC" el calendario
     las muestra tal cual, sin depender de la zona horaria del navegador. */
  var calendario = new FullCalendar.Calendar(el, {
    locale: 'es',
    timeZone: 'UTC',
    initialView: window.innerWidth < 768 ? 'listWeek' : 'timeGridWeek',
    headerToolbar: { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,timeGridDay,listWeek' },
    buttonText: { today: 'Hoy', month: 'Mes', week: 'Semana', day: 'Día', list: 'Lista' },
    slotMinTime: '07:00:00',
    slotMaxTime: '21:00:00',
    allDaySlot: false,
    nowIndicator: false,
    height: 'auto',
    firstDay: 1,
    eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
    events: function (info, ok, fallo) {
      var url = el.dataset.url + '?start=' + encodeURIComponent(info.startStr) + '&end=' + encodeURIComponent(info.endStr);
      MG.json(url).then(ok).catch(fallo);
    },
    eventDidMount: function (arg) {
      arg.el.title = arg.event.title + ' — ' + (arg.event.extendedProps.estado || '');
    }
  });
  calendario.render();
})();
