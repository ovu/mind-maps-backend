package mindmaps.http

import zio._
import zio.http._

/** Serves the OpenAPI spec and a Swagger UI page.
  *
  *   GET /docs           — interactive Swagger UI (loads spec from /docs/openapi.yaml)
  *   GET /docs/openapi.yaml — raw OpenAPI 3.0 YAML spec
  */
object DocsRoutes {

  private val openApiYaml: String = {
    val stream = getClass.getClassLoader.getResourceAsStream("openapi.yaml")
    try scala.io.Source.fromInputStream(stream).mkString
    finally stream.close()
  }

  private val swaggerUiHtml: String =
    """<!DOCTYPE html>
      |<html lang="en">
      |<head>
      |  <meta charset="UTF-8" />
      |  <title>Mind Maps API — Swagger UI</title>
      |  <link rel="stylesheet"
      |        href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.17.14/swagger-ui.css" />
      |</head>
      |<body>
      |<div id="swagger-ui"></div>
      |<script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.17.14/swagger-ui-bundle.js"></script>
      |<script>
      |  SwaggerUIBundle({
      |    url: '/docs/openapi.yaml',
      |    dom_id: '#swagger-ui',
      |    presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
      |    layout: 'BaseLayout'
      |  });
      |</script>
      |</body>
      |</html>""".stripMargin

  val routes: Routes[Any, Nothing] = Routes(
    Method.GET / "docs" -> Handler.fromFunction[Request] { _ =>
      Response(
        status  = Status.Ok,
        headers = Headers(Header.ContentType(MediaType.text.html)),
        body    = Body.fromString(swaggerUiHtml)
      )
    },
    Method.GET / "docs" / "openapi.yaml" -> Handler.fromFunction[Request] { _ =>
      Response(
        status  = Status.Ok,
        headers = Headers(Header.ContentType(MediaType("text", "yaml"))),
        body    = Body.fromString(openApiYaml)
      )
    }
  )
}
