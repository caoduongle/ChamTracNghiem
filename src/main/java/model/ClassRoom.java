package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ClassRoom implements Serializable {
    public String className;
    public List<Student> students = new ArrayList<>();

    public static class Student implements Serializable {
        public int stt;
        public String name;

        public Student(int stt, String name) {
            this.stt = stt;
            this.name = name;
        }
    }
}