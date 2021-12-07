package com.programming_distributed_systems_project;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class represents a single thread which will be given to each client connected to the server at a particular time
 * All call and responses made between the client and server are handled here on the server side
 */
public class Socket_Connection implements Runnable{
    private static ConcurrentHashMap<Integer, User> users = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Team> teams = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, Script> scripts = new ConcurrentHashMap<>();
    private Socket connection;  // Create Socket
    private ObjectInputStream clientCall;
    private ObjectOutputStream serverResponse;
    private User user;
    private Request request;

    public Socket_Connection(Socket s, Request request) {
        this.connection = s;
        this.request = request;
    }

    public Socket_Connection(Socket connection) {

    }

    @Override
    public void run() {
        try {
            while(true) {
                clientCall = new ObjectInputStream(connection.getInputStream()); //Create a call Buffer
                Call call = (Call) clientCall.readObject(); //Read Client call, Convert it to String
                System.out.println("Client sent : " + call.toString()); //Print the client call
                handleCall(call, request.getUserId());
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("connection closed");
//                e.printStackTrace();
            this.closeSocket();
        }
    }

    /**
     * close the socket  if the user disconnects
     */
    private void closeSocket() {
        try {
            if(clientCall != null) clientCall.close();
            if(serverResponse != null) serverResponse.close();
            if(connection != null) connection.close();
        } catch (IOException e) {
            System.out.println("Couldn't close server");
//            e.printStackTrace();
        }

    }

    /**
     * Handles all call made from client to server
     * @param call
     * @param userId
     * @throws IOException
     */
    private void handleCall(Call call, Integer userId) throws IOException {
        String operation = call.getOperation();
        switch (operation) {
            case "register":
                this.register(call, userId);
                break;
            case "login":
                this.login(call);
                break;

            case "join team":
                this.joinTeam(request);
                break;
        }
    }

    /**
     * Adds a user to a team
     * @param request
     */
    private void joinTeam(Request request) throws IOException {
        int teamId = request.getTeamId();
        Team team = teams.get(teamId);
        if(!team.isFull()) {
            this.addReaderToTeam(team);
            if(team.isFull()) {
                synchronized (team) {
                    team = this.addTeamScript(team.getId());
                    this.notifyClient("Successfully joined team" + UserInterface.newLine() + "Time to choose a character", user, team.getScript(), "choose character", connection);
                    this.setChooseCharacterTimeout(team);
                    team.notifyAll();
                }
            } else {
                this.notifyClient("Successfully joined team", user, null, "wait", connection);
                this.waitForTeamCompletion(team);
            }
        } else {
            ArrayList<Integer> availableTeams = getAvailableTeams();
            if(availableTeams.size() < 1) {
                Team newTeam = createTeam();
                availableTeams.add(newTeam.getId());
                this.addReaderToTeam(newTeam);
                this.notifyClient("You have been  automatically added to team" + newTeam.getId(), user, null, "wait", connection);
            } else {
                this.notifyClient("Team"+ teamId + " unfortunately is full", user, availableTeams, "choose team", connection);
            }
        }
    }

    private Team createTeam() {
        synchronized (teams) {
            int numberOfTeams = teams.size();
            int teamId = numberOfTeams + 1;
            String newTeamName = "team" + teamId;
            Team newTeam = new Team(teamId, newTeamName);
            teams.put(teamId, newTeam);
            return newTeam;
        }
    }

    private ArrayList<Integer> getAvailableTeams() {
        ArrayList<Integer> availableTeams = new ArrayList<>();
        teams.forEach((k, v) -> {
            if(!v.isFull()) {
                availableTeams.add(v.getId());
            }
        });
        return availableTeams;
    }

    private void waitForTeamCompletion(Team team) throws IOException {
        synchronized (team) {
            try {
                team.wait();
                if(team.isFull()) {
                    this.notifyClient("Time to choose a character, you have 10 seconds",user, team.getScript(), "choose character",connection);
                    setChooseCharacterTimeout(team);
                }
            } catch (InterruptedException e) {
                System.out.println("team addition is interrupted is interrupted");
                e.printStackTrace();
            }
        }
    }
    private void setChooseCharacterTimeout(Team team) {
        setTimeout(() -> {
            synchronized (team) {
                if(team.getAssignedCharacters().size() != 3) {
                    assignCharacters(team);
                }
            }
        }, 10000);

    }

    public static void setTimeout(Runnable runnable, int delay){
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                runnable.run();
            }
            catch (Exception e){
                System.err.println(e);
            }
        }).start();
    }

    /**
     * Assigns random characters to each user of a team if time finishes and user hasn't chosen a character yet
     * @param team team to assign characters to
     */
    private synchronized void assignCharacters(Team team) {
        HashMap<Integer, Reader> readers = team.getReaders();
        Script script = team.getScript();
        ArrayList<Character> characters = script.getCharacters();
        ArrayList<Character> assignedCharacters = team.getAssignedCharacters();

        if(assignedCharacters.size() != 3) {
            Reader reader = team.getReader(user.getUserId());
            Character userCharacter = reader.getCharacter();
            if (userCharacter == null) {
                for(char character : characters) {
                    if(!team.getAssignedCharacters().contains(character)) {
                        reader.setCharacter(character);
                        team.setAssignedCharacters(character);
                        readers.forEach((k, v) -> {
                            this.notifyClient(user.getUsername() + " chose " + reader.getCharacter() , user, null, "chosen character", v.getConnection());
                        });
                        break;
                    }
                }
            }
        }
        // Print round results when the all users have characters
        if(assignedCharacters.size() == 3) {
            readers.forEach((k, v) -> {
                this.notifyClient("******************************************" + UserInterface.newLine() + "Here are the results of the game" + UserInterface.newLine() + "************************************" + UserInterface.newLine() + team.printRankingResults() + "*******************************************", null, null, "end game", v.getConnection());
            });
        }
    }

    private Team addTeamScript(int teamId) {
        synchronized (teams) {
            Team team = teams.get(teamId);
            int teamRankingAverage = team.getTeamRankingAverage();
            Script script = new Script(teamRankingAverage);
            if(team.getScript() == null) {
                team.setScript(script);
            }
            return team;
        }
    }

    /**
     * Adds a reader to a specific team
     * @param team
     * @return team which the user was added to
     */
    private Team addReaderToTeam(Team team) {
        Reader reader = new Reader(user.getUserId(), user.getUsername(), connection);
        user.setTeamId(team.getId());
        team.setReader(reader);
        return team;
    }

    /**
     * This function can be used to perform server side register user functionality
     */
    private synchronized void register(Call call, Integer userId) throws IOException {

        String username = call.getUsername();
        String password = call.getPassword();
        boolean freeUserName = true;
        for(int i =  1; i <= users.size(); i++) {
            if(users.isEmpty() || call == null) {
                break;
            }
            User _user = users.get(i);
            String userName = _user.getUsername();
            if(userName.equals(username)) {
                freeUserName = false;
                break;
            }
        }
        if (freeUserName) {
            User user = new User(username, password, userId);
            users.put(userId, user);

            this.notifyClient("Successfully registered", null, null, "login", connection);
        } else {
            this.notifyClient("The username is taken", null, null, "retry", connection);
        }
    }

    /**
     * This function can be used to perform server side login user functionality
     */
    private synchronized void login(Call call) throws IOException {
        String password = call.getPassword();
        String username = call.getUsername();
        for(int i =  1; i <= users.size(); i++) {
            if(users.isEmpty() || call == null) {
                break;
            }
            User _user = users.get(i);
            String userPassword = _user.getPassword();
            String userName = _user.getUsername();
            if(userName.equals(username) && userPassword.equals(password)) {
                user = _user;
                break;
            }
        }
    }


    /**
     * Sends all responses from server to client
     * @param response
     */
    private void notifyClient(String response, User user, Object responseData, String nextOperation, Socket connection) {
        try {
            Response response1 = new Response(response, user, responseData, nextOperation);
            serverResponse = new ObjectOutputStream(connection.getOutputStream()); //Create a Response Buffer
            serverResponse.writeObject(response1); //write "Response" in the outputStream
            serverResponse.flush(); //Send written content to client
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
