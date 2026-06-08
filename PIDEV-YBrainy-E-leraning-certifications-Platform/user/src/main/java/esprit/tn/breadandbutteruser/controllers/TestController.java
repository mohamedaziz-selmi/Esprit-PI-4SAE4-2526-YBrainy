package esprit.tn.breadandbutteruser.controllers;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/users/test")
    public String test() {
        return "JWT OK - user service reached";
    }
}
