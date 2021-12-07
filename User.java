package com.programming_distributed_systems_project;

import java.io.Serializable;

/**
 * This class represents each user in users stored on the server
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private int userId;
    private Integer teamId;



    public User (String username, String password,int userId) {

        this.username = username;
        this.password = password;
        this.userId = userId;


    }
    public String getPassword() {
        return password;
    }
    public String getUsername() {
        return username;
    }


    public int getUserId() {
        return userId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public Integer getTeamId() {

        return this.teamId;
    }

}