(function () {
  'use strict';
  var modal = document.getElementById('modalRoles');
  if (!modal) { return; }

  modal.addEventListener('show.bs.modal', function (e) {
    var d = e.relatedTarget.dataset;
    document.getElementById('formRoles').action = modal.dataset.base + '/' + encodeURIComponent(d.id) + '/roles';
    document.getElementById('correoRoles').textContent = d.email;
    var actuales = (d.roles || '').split(',');
    Array.prototype.forEach.call(modal.querySelectorAll('input[name="roles"]'), function (c) {
      c.checked = actuales.indexOf(c.value) >= 0;
    });
  });
})();
