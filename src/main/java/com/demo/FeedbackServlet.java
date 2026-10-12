package com.demo;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/submit")
public class FeedbackServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        String name = escape(req.getParameter("name"));
        String email = escape(req.getParameter("email"));
        String message = escape(req.getParameter("message"));
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().write("<html><body><h2>Thank you, " + name + "!</h2>"
            + "<p>Email: " + email + "</p><p>Feedback: " + message + "</p>"
            + "<a href='index.html'>Back</a></body></html>");
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
