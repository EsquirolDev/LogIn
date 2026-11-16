import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class GestorUsuarios {

    private static final String ARCHIVO = "usuarios.txt";
    private static final int ITERACIONES = 210000;

    // Comprueba si un correo ya está registrado
    public boolean existeUsuario(String email) throws IOException {

        return buscarUsuario(email) != null;
    }

    // Busca un usuario por su correo
    public Usuario buscarUsuario(String email) throws IOException {

        if (!Files.exists(Paths.get(ARCHIVO))) {
            return null;
        }

        for (String linea : Files.readAllLines(Paths.get(ARCHIVO))) {

            String[] datos = linea.split(";", -1);

            if (datos.length == 4 &&
                    datos[1].equalsIgnoreCase(email)) {

                return new Usuario(
                        datos[0],
                        datos[1],
                        datos[2],
                        datos[3]
                );
            }
        }

        return null;
    }

    // Registra un usuario nuevo
    public boolean registrarUsuario(
            String nombre, String email, String password)
            throws Exception {

        if (existeUsuario(email)) {
            return false;
        }

        String salt = generarSalt();
        String hash = generarHash(password, salt);

        Usuario usuario = new Usuario(
                nombre, email, hash, salt
        );

        guardarUsuario(usuario);

        return true;
    }

    // Guarda el usuario en el archivo
    private void guardarUsuario(Usuario usuario)
            throws IOException {

        String linea =
                usuario.getNombre() + ";" +
                        usuario.getEmail() + ";" +
                        usuario.getPasswordHash() + ";" +
                        usuario.getSalt();

        Files.write(
                Paths.get(ARCHIVO),
                (linea + System.lineSeparator()).getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    // Genera una sal aleatoria
    private String generarSalt() {

        byte[] salt = new byte[16];

        new SecureRandom().nextBytes(salt);

        return Base64.getEncoder().encodeToString(salt);
    }

    // Genera el hash de la contraseña
    private String generarHash(String password, String salt)
            throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                Base64.getDecoder().decode(salt),
                ITERACIONES,
                256
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return Base64.getEncoder().encodeToString(
                    factory.generateSecret(spec).getEncoded()
            );

        } finally {
            spec.clearPassword();
        }
    }

    // Comprueba si la contraseña es correcta
    public boolean comprobarPassword(
            String password, Usuario usuario) throws Exception {

        String hash = generarHash(
                password, usuario.getSalt()
        );

        return MessageDigest.isEqual(
                Base64.getDecoder().decode(hash),
                Base64.getDecoder().decode(usuario.getPasswordHash())
        );
    }
}