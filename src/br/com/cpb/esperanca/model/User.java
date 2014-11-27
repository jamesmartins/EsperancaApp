package br.com.cpb.esperanca.model;

public class User {
    
    public int id;
    public String username, password, nome, sobrenome;

    public String getFullName() {
        return nome + " " + sobrenome;
    }

}
