public class Usuario {

    private String nombre;
    private String email;
    private String passwordHash;
    private String salt;

    public Usuario(String nombre, String email,
                   String passwordHash, String salt) {

        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }
}