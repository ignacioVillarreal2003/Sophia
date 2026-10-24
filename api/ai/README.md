# Sophia AI API

Servicio de IA de Sophia: genera material de estudio a partir de los documentos de un espacio de trabajo y conversa con
ellos. Spring Boot 3.5 / Java 21, puerto **8083**, base propia `sophia_ai`, Redis para los límites de uso.
El modelo es Google Gemini por su API REST (las mismas variables `GEMINI_*` que usan los servicios de Lumen).

## Qué hace

| Grupo | Funciones | Endpoint |
| --- | --- | --- |
| Crear | Apuntes de un tema o de documentos (streaming) | `POST /api/v1/ai/notes` |
| | Tablero de tarjetas o flashcards | `POST /api/v1/ai/deck` |
| | Mapa mental o conceptual | `POST /api/v1/ai/mindmap` |
| | Reescribir / explicar una selección, continuar el texto (streaming) | `POST /api/v1/ai/rewrite`, `/continue` |
| Estudiar | Cuestionarios y exámenes simulacro, con corrección de respuestas cortas | `POST /api/v1/ai/quiz`, `/quiz/grade` |
| | Plan de estudio hasta la fecha del examen | `POST /api/v1/ai/study-plan` |
| | Resumen de repaso con los puntos débiles (streaming) | `POST /api/v1/ai/review-summary` |
| Analizar | Perspectivas, debate a favor y en contra, revisión de apuntes ("qué me falta") | `POST /api/v1/ai/perspectives`, `/debate`, `/gaps` |
| Convertir | Escanear una foto o un PDF a texto (streaming, tal cual o como apuntes ordenados) | `POST /api/v1/ai/ocr` (form) |
| | Tabla de un texto, documentos, una foto o un PDF | `POST /api/v1/ai/tables`, `/tables/file` |
| | Diagrama de flujo, línea de tiempo o ciclo | `POST /api/v1/ai/diagrams` |
| | Título, resumen y etiquetas de una nota | `POST /api/v1/ai/tags` |
| | Versión "para escuchar" de un texto (la voz es la del navegador) | `POST /api/v1/ai/listen` |
| Conversar | Chat con documentos (con citas), tutor socrático, compañero de debate | `/api/v1/ai/conversations` |
| Administración | Activar/desactivar, límites diarios, consumo | `/api/v1/admin/ai/settings`, `/usage` |

Los resultados son **borradores**: el servicio no escribe en el espacio de trabajo. El frontend los convierte en
documentos con el token de la persona (tablero, pizarra, lista de tareas, nota).

## Cómo funciona

- **Permisos.** Los documentos se leen del servicio `document-workspace` con el `Authorization` de quien pregunta,
  en cada petición. La IA nunca ve algo que la persona no pueda abrir, y una conversación solo la abre su dueño.
- **Sin recortes silenciosos.** Si el material no entra (`max-source-chars`, `max-sources`) la petición se rechaza
  diciendo cuánto hay, en lugar de responder con la mitad de lo elegido. Las conversaciones, en cambio, buscan.
- **Texto de los documentos = datos.** Va entre etiquetas `<document>` y el prompt de sistema lo declara; las etiquetas
  de cierre dentro del texto se neutralizan.
- **Resultados estructurados.** Mapas, cuestionarios, planes, etc. se piden con salida estructurada (JSON Schema) y
  `ResultSanitizer`/`AnalysisSanitizer` acotan y validan lo que vuelve; lo inutilizable se descarta, y un resultado
  vacío es un error. Un JSON ilegible se reintenta una vez.
- **Streaming.** Los textos largos salen por server-sent events (`meta`, `delta`*, `done` | `error`) con pings de
  latido. Si el lector se va, se cierra el stream hacia el modelo y se deja de gastar.
- **Chat con documentos.** Si los documentos caben en `chat.full-context-chars` viajan enteros en el prompt de sistema
  (se cachea entre turnos). Si no, se indexan por texto completo en PostgreSQL (`ai_doc_chunks`, `tsvector`) y solo
  viajan los pasajes que responden a la pregunta. Es búsqueda léxica a propósito: el proveedor no ofrece embeddings y
  para apuntes propios, que repiten sus palabras, alcanza bien. `DocumentIndex` es el único punto a cambiar para pasar
  a vectores.
- **Límites.** Cupo diario de tokens (UTC) por persona y por espacio de trabajo, editable en caliente desde
  Administración (0 = sin límite); máximo de generaciones simultáneas por persona; ventana de peticiones más estricta
  para los `POST` que usan el modelo. No se guardan prompts ni respuestas en el registro de consumo; las conversaciones
  guardan los mensajes y se borran tras `chat.retention` sin actividad.

- **Archivos subidos.** El tipo se decide por los primeros bytes (PNG, JPEG, GIF, WebP, PDF), no por el nombre ni el
  `Content-Type`; imágenes hasta 5 MB, PDF hasta 15 MB y unas 50 páginas (conteo aproximado). El archivo viaja al
  proveedor y no se guarda en Sophia. Las fotos y los PDF consumen más tokens que el texto: cuentan en el mismo cupo.
- **Varias instancias.** "Una respuesta a la vez por conversación" y "máximo de generaciones simultáneas por persona"
  viven en Redis con caducidad; si Redis cae, cada instancia cuenta por su cuenta en lugar de rechazar todo.
- **Privacidad.** `DELETE /api/v1/ai/conversations` borra todas las conversaciones de quien lo llama. Con
  `AI_PROVIDER=mock` el servicio no arranca bajo el perfil `prod`.

## Configuración

| Variable | Por defecto | Qué es |
| --- | --- | --- |
| `GEMINI_API_KEY` | _(vacía)_ | Clave del proveedor. Sin ella el servicio arranca y responde `AI_NOT_CONFIGURED`. |
| `AI_PROVIDER` | `gemini` | `mock` devuelve respuestas de ejemplo, solo para trabajar la interfaz y los tests. |
| `GEMINI_MODEL`, `GEMINI_API_BASE_URL` | `gemini-3.6-flash`, API v1beta de Google | Modelo y dirección de la API. |
| `AI_ENABLED` | `true` | Valor inicial del interruptor (después se cambia desde Administración). |
| `AI_DAILY_TOKENS_PER_USER` / `AI_DAILY_TOKENS_PER_WORKSPACE` | `400000` / `2000000` | Valores iniciales de los cupos. |
| `AI_MAX_SOURCE_CHARS`, `AI_MAX_SOURCES` | `120000`, `12` | Material que admite una generación. |
| `AI_CHAT_*` | ver `application.yml` | Contexto completo vs. búsqueda, historial, límites y retención de conversaciones. |
| `AI_MAX_IMAGE_BYTES`, `AI_MAX_PDF_BYTES`, `AI_MAX_PDF_PAGES` | `5 MB`, `15 MB`, `50` | Límites de archivos subidos. |
| `WORKSPACE_API_URL` | `http://localhost:8081` | Servicio de documentos. |

El resto (base de datos, Redis, JWT, CORS, trazas) sigue las mismas variables que los otros servicios.

## Ejecutar y probar

```bash
# desde api/docker, con la infraestructura, authentication y document-workspace arriba
docker compose -f docker-compose.ai.yml up -d --build
```

```bash
cd api/ai
./mvnw test
```

Prueba de punta a punta (con el stack arriba y `AI_PROVIDER=mock`): `py api/ai/e2e/e2e_ai.py`. Manifiestos de
Kubernetes en `k8s/`.

Swagger: http://localhost:8083/swagger-ui.html. En producción el frontend entra por `/ai/` (nginx, sin buffering
para el streaming).
