package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ClassRoom implements Serializable {
    public String className;

    // Khai báo danh sách học sinh
    public List<Student> students = new ArrayList<>();

    // LƯU Ý QUAN TRỌNG: Class Student phải nằm LỌT THỎM bên trong class ClassRoom
    public static class Student implements Serializable {
        public int stt;
        public String name;

        public Student(int stt, String name) {
            this.stt = stt;
            this.name = name;
        }
    }
}