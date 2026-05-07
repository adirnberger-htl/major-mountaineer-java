module org.example.majorgame {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.majorgame to javafx.fxml;
    exports org.example.majorgame;
}