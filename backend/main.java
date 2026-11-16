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

    public static void main(String[] args) throws IOException {

        HttpServer servidor = HttpServer.create(
                new InetSocketAddress(8080), 0
        );

        servidor.createContext("/api/registro", main::registrar);

        servidor.setExecutor(null);
        servidor.start();

        System.out.println("Servidor iniciado en http://localhost:8080");
    }

    // Gestiona el registro de usuarios
    private static void registrar(HttpExchange exchange)
            throws IOException {

        // Configuración CORS para el frontend
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods", "POST, OPTIONS"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
        );

        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equals("POST")) {
            responder(exchange, 405, "Método no permitido");
            return;
        }

        try {

            String cuerpo = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            Map<String, String> datos = obtenerDatos(cuerpo);

            String nombre = datos.get("nombre");
            String email = datos.get("email");
            String password = datos.get("password");
            String confirmar = datos.get("confirmar");

            if (nombre == null || email == null ||
                    password == null || confirmar == null ||
                    nombre.isBlank() || email.isBlank()) {

                responder(exchange, 400, "Faltan campos obligatorios");
                return;
            }

            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                responder(exchange, 400, "Correo no válido");
                return;
            }

            if (password.length() < 8) {
                responder(exchange, 400, "Contraseña demasiado corta");
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

            responder(exchange, 201, "Usuario registrado correctamente");

        } catch (Exception e) {

            e.printStackTrace();
            responder(exchange, 500, "Error interno del servidor");
        }
    }

    // Convierte los parámetros recibidos en un Map
    private static Map<String, String> obtenerDatos(String cuerpo) {

        Map<String, String> datos = new HashMap<>();

        for (String parametro : cuerpo.split("&")) {

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

    // Envía una respuesta JSON
    private static void responder(
            HttpExchange exchange, int codigo, String mensaje)
            throws IOException {

        String json = "{\"mensaje\":\"" + mensaje + "\"}";

        byte[] respuesta = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(codigo, respuesta.length);

        try (OutputStream salida = exchange.getResponseBody()) {
            salida.write(respuesta);
        }
    }
}