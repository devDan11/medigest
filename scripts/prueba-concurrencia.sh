#!/usr/bin/env bash
# Prueba de concurrencia de reservas de MediGest.
# Requiere la app en marcha (perfil dev,demo), curl y python3.
#
# Uso:   ADMIN_PASSWORD='tu-clave-demo' ./scripts/prueba-concurrencia.sh
# Vars:  BASE_URL (http://localhost:8080)  ADMIN_EMAIL (admin@medigest.test)  N (10 peticiones paralelas)
set -u
BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@medigest.test}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Defina ADMIN_PASSWORD (la clave demo con la que se crearon los usuarios)}"
N="${N:-10}"
API="$BASE_URL/api/v1"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FALLOS=0

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null; }

TOKEN=$(curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | json "d['accessToken']")
[ -n "${TOKEN:-}" ] || { echo "No se pudo iniciar sesión (revise ADMIN_EMAIL / ADMIN_PASSWORD)"; exit 2; }
AUTH=(-H "Authorization: Bearer $TOKEN")

# --- localizar un médico con horario libre ---------------------------------------------------
DESDE=$(python3 -c "import datetime as d; print(d.date.today()+d.timedelta(days=2))")
HASTA=$(python3 -c "import datetime as d; print(d.date.today()+d.timedelta(days=13))")
ESPECIALIDADES=$(curl -s "${AUTH[@]}" "$API/especialidades" | json "' '.join(str(e['id']) for e in d)")
MEDICO=""; ESP=""; INICIO=""
for e in $ESPECIALIDADES; do
  R=$(curl -s "${AUTH[@]}" "$API/disponibilidad/medicos?especialidadId=$e&desde=$DESDE&hasta=$HASTA")
  OUT=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for m in d:
    h = m.get('proximoHorario')
    if h:
        print(m['medicoId'], h['inicio']); break
" 2>/dev/null)
  if [ -n "$OUT" ]; then MEDICO=${OUT%% *}; INICIO=${OUT#* }; ESP=$e; break; fi
done
[ -n "$MEDICO" ] || { echo "No se encontró ningún horario libre en $DESDE..$HASTA"; exit 2; }
echo "Médico $MEDICO, especialidad $ESP, horario $INICIO"

PACIENTES=($(curl -s "${AUTH[@]}" "$API/pacientes?tamano=$((N+5))&activo=true" | json "' '.join(str(p['id']) for p in d['contenido'])"))
[ "${#PACIENTES[@]}" -ge 2 ] || { echo "Se necesitan al menos 2 pacientes demo"; exit 2; }
[ "${#PACIENTES[@]}" -lt "$N" ] && N=${#PACIENTES[@]}

reservar() { # $1 paciente  $2 archivo de salida
  curl -s -o "$2.body" -w '%{http_code}' -X POST "$API/citas" "${AUTH[@]}" -H 'Content-Type: application/json' \
    -d "{\"pacienteId\":$1,\"medicoId\":$MEDICO,\"especialidadId\":$ESP,\"inicio\":\"$INICIO\",\"motivo\":\"prueba concurrencia\"}" > "$2"
}

resumen() { # $1 prefijo
  OK=0; CONF=0; OTROS=0
  for f in "$TMP"/$1-*; do
    case "$f" in *.body) continue;; esac
    c=$(cat "$f")
    case "$c" in 201) OK=$((OK+1));; 409) CONF=$((CONF+1)); echo "  409 -> $(json "d.get('codigo')" < "$f.body")";; *) OTROS=$((OTROS+1)); echo "  respuesta inesperada $c: $(cat "$f.body")";; esac
  done
}

limpiar() { # $1 prefijo
  for f in "$TMP"/$1-*.body; do
    ID=$(json "d['id']" < "$f"); [ -n "$ID" ] && curl -s -w '  limpieza cita %{http_code}\n' -o /dev/null -X PATCH "$API/citas/$ID/cancelar" "${AUTH[@]}" \
      -H 'Content-Type: application/json' -d '{"motivo":"limpieza de prueba"}'
  done
}

# --- Prueba A: N pacientes distintos, mismo horario -----------------------------------------
echo; echo "== A: $N pacientes distintos reservan el mismo horario a la vez"
for i in $(seq 0 $((N-1))); do reservar "${PACIENTES[$i]}" "$TMP/A-$i" & done; wait
resumen A
echo "  creadas=$OK conflictos=$CONF otros=$OTROS"
if [ "$OK" -eq 1 ] && [ "$OTROS" -eq 0 ]; then echo "  PASS"; else echo "  FAIL (se esperaba 1 creada y el resto 409)"; FALLOS=$((FALLOS+1)); fi
limpiar A

# --- Prueba B: el mismo paciente, mismo horario, N veces (doble clic) ------------------------
echo; echo "== B: el mismo paciente envía $N peticiones iguales a la vez"
for i in $(seq 0 $((N-1))); do reservar "${PACIENTES[0]}" "$TMP/B-$i" & done; wait
resumen B
echo "  creadas=$OK conflictos=$CONF otros=$OTROS"
if [ "$OK" -eq 1 ] && [ "$OTROS" -eq 0 ]; then echo "  PASS"; else echo "  FAIL (se esperaba 1 creada y el resto 409)"; FALLOS=$((FALLOS+1)); fi
limpiar B

echo
if [ "$FALLOS" -eq 0 ]; then echo "RESULTADO: todas las pruebas de concurrencia PASARON"; else echo "RESULTADO: $FALLOS prueba(s) FALLARON"; exit 1; fi
