package com.leojay.simplewgad.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class LoginController {
    @GetMapping("/login")
    fun loginPage(): String {
        return "login"
    }

    @PostMapping("/login")
    @ResponseBody
    fun login(@RequestParam password : String): String {
        return if (password == "12345678asd") {
            "success"
        }else {
            "fail"
        }

    }
}
