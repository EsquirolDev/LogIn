import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLConnection;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class main {

    private static final GestorUsuarios gestor = new GestorUsuarios();

    private static void servirFrontend(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equals("GET") &&
                !exchange.getRequestMethod().equals("HEAD")) {

            responder(exchange, 405, "Método no permitido");
            return;
        }

        String ruta = exchange.getRequestURI().getPath();

        if (ruta.equals("/")) {
            ruta = "/login.html";
        }

        Path carpetaFrontend = Path.of("..", "frontend")
                .toAbsolutePath().normalize();

        Path archivo = carpetaFrontend.resolve(
                ruta.substring(1)
        ).normalize();

        if (!archivo.startsWith(carpetaFrontend) ||
                !Files.isRegularFile(archivo)) {

            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String tipo = URLConnection.guessContentTypeFromName(
                archivo.toString()
        );

        if (tipo == null) {
            tipo = "application/octet-stream";
        }

        if (archivo.toString().endsWith(".css")) {
            tipo = "text/css; charset=UTF-8";
        }

        if (archivo.toString().endsWith(".html")) {
            tipo = "text/html; charset=UTF-8";
        }

        byte[] contenido = Files.readAllBytes(archivo);

        exchange.getResponseHeaders().set(
                "Content-Type", tipo
        );

        exchange.getResponseHeaders().set(
                "X-Content-Type-Options", "nosniff"
        );

        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(200, contenido.length);

        try (OutputStream salida = exchange.getResponseBody()) {
            salida.write(contenido);
        }
    }

    public static void main(String[] args) throws IOException {

        HttpServer servidor = HttpServer.create(
                new InetSocketAddress(8080), 0
        );

        servidor.createContext("/api/registro", main::registrar);
        servidor.createContext("/api/login", main::login);
        servidor.createContext("/", main::servirFrontend);

        servidor.setExecutor(null);
        servidor.start();

        System.out.println("Servidor iniciado en http://localhost:8080");
        System.out.println("Registro: POST /api/registro");
        System.out.println("Login: POST /api/login");
    }

    private static void redirigir(
            HttpExchange exchange, String destino) throws IOException {

        exchange.getResponseHeaders().set(
                "Location", destino
        );

        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private static void configurar(HttpExchange exchange) {

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );
    }

    private static void registrar(HttpExchange exchange)
            throws IOException {

        configurar(exchange);

        if (!exchange.getRequestMethod().equals("POST")) {
            responder(exchange, 405, "Método no permitido");
            return;
        }

        try {

            Map<String, String> datos = obtenerDatos(exchange);

            String nombre = datos.get("nombre");
            String email = datos.get("email");
            String password = datos.get("password");
            String confirmar = datos.get("confirmar");

            if (nombre == null || email == null ||
                    password == null || confirmar == null ||
                    nombre.isBlank() || email.isBlank() ||
                    password.isBlank() || confirmar.isBlank()) {

                responder(exchange, 400, "Todos los campos son obligatorios");
                return;
            }

            nombre = nombre.trim();
            email = email.trim().toLowerCase();

            if (nombre.length() > 100 || email.length() > 254) {
                responder(exchange, 400, "Nombre o correo demasiado largos");
                return;
            }

            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                responder(exchange, 400, "Correo no válido");
                return;
            }

            if (password.length() < 8) {
                responder(exchange, 400,
                        "La contraseña debe tener al menos 8 caracteres");
                return;
            }

            if (!password.equals(confirmar)) {
                responder(exchange, 400, "Las contraseñas no coinciden");
                return;
            }

            boolean registrado = gestor.registrarUsuario(
                    nombre, email, password
            );

            if (!registrado) {
                responder(exchange, 409, "El correo ya está registrado");
                return;
            }

            redirigir(exchange, "/registro-exitoso.html");

        } catch (IllegalArgumentException e) {

            responder(exchange, 400, "Datos del formulario no válidos");

        } catch (Exception e) {

            e.printStackTrace();
            responder(exchange, 500, "Error interno del servidor");
        }
    }

    private static void login(HttpExchange exchange)
            throws IOException {

        configurar(exchange);

        if (!exchange.getRequestMethod().equals("POST")) {
            responder(exchange, 405, "Método no permitido");
            return;
        }

        try {

            Map<String, String> datos = obtenerDatos(exchange);

            String email = datos.get("email");
            String password = datos.get("password");

            if (email == null || password == null ||
                    email.isBlank() || password.isBlank()) {

                responder(exchange, 400,
                        "Introduce correo y contraseña");
                return;
            }

            email = email.trim().toLowerCase();

            Usuario usuario = gestor.buscarUsuario(email);

            if (usuario == null ||
                    !gestor.comprobarPassword(password, usuario)) {

                responder(exchange, 401,
                        "Correo o contraseña incorrectos");
                return;
            }

            responder(exchange, 200,
                    "Inicio de sesión correcto");

        } catch (IllegalArgumentException e) {

            responder(exchange, 400, "Datos del formulario no válidos");

        } catch (Exception e) {

            e.printStackTrace();
            responder(exchange, 500, "Error interno del servidor");
        }
    }

    private static Map<String, String> obtenerDatos(
            HttpExchange exchange) throws IOException {

        String tipo = exchange.getRequestHeaders()
                .getFirst("Content-Type");

        if (tipo == null ||
                !tipo.toLowerCase().startsWith(
                        "application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Formato no válido");
        }

        byte[] cuerpo = exchange.getRequestBody().readNBytes(16385);

        if (cuerpo.length > 16384) {
            throw new IllegalArgumentException("Petición demasiado grande");
        }

        String contenido = new String(
                cuerpo, StandardCharsets.UTF_8
        );

        Map<String, String> datos = new HashMap<>();

        for (String parametro : contenido.split("&")) {

            String[] partes = parametro.split("=", 2);

            if (partes.length == 2) {

                String clave = URLDecoder.decode(
                        partes[0], StandardCharsets.UTF_8
                );

                String valor = URLDecoder.decode(
                        partes[1], StandardCharsets.UTF_8
                );

                datos.put(clave, valor);
            }
        }

        return datos;
    }

    private static void responder(
            HttpExchange exchange,
            int codigo,
            String mensaje) throws IOException {

        String json = "{\"mensaje\":\"" + escaparJSON(mensaje) + "\"}";

        byte[] respuesta = json.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(codigo, respuesta.length);

        try (OutputStream salida = exchange.getResponseBody()) {
            salida.write(respuesta);
        }
    }

    private static String escaparJSON(String texto) {

        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}