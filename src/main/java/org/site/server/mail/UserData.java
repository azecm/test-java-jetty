package org.site.server.mail;

import java.util.ArrayList;
import java.util.List;

public class UserData {

    public static class Event {
        public String date;
        public int delta;
        public String period;
    }

    public static class Note {
        public long id;
        public String name;
        public String email;
        public Event event;

        public Note(long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }

    public static class Group {
        public long id;
        public String name;
        public List<Note> note;
    }

    public String name;
    public String pass;
    public String email;
    public String signature;
    public List<Group> notes = new ArrayList<>();
}
