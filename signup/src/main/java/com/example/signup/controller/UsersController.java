package com.example.signup.controller;

//import com.example.signup.languages.MyLocaleResolver;
import com.example.signup.audit.AuditLog;
import com.example.signup.audit.AuditLogRepository;
import com.example.signup.audit.AuditService;
import com.example.signup.modal.Movie;
import com.example.signup.modal.UsersModel;
import com.example.signup.service.MovieService;
import com.example.signup.service.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class UsersController {

    private final UsersService usersService;
    private final MovieService movieService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private static final Logger log = LoggerFactory.getLogger(MovieController.class);

    @Autowired
    private MessageSource messageSource;

//    @Autowired
////    private MyLocaleResolver myLocaleResolver;

    @Autowired
    public UsersController(UsersService usersService, MovieService movieService,AuditService auditService, AuditLogRepository auditLogRepository) {
        this.usersService = usersService;
        this.movieService = movieService;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;


    }



    @GetMapping("/")
    public String getIndex() {
        return "index";
    }

    @GetMapping("/register")
    public String getRegisterPage(Model model) {
        model.addAttribute("registerRequest", new UsersModel());
        return "register_page";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute UsersModel usersModel, Model model) {
        UsersModel registeredUser = usersService.registerUser(usersModel.getLogin(), usersModel.getPassword(), usersModel.getEmail(), usersModel.getRole());
        if (registeredUser == null) {
            model.addAttribute("error", "Registration failed. Username may already exist.");
            return "register_page";
        }
        auditService.logAction("REGISTER", usersModel.getLogin(), "User registered successfully");
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String getLoginPage() {
        return "login_page";
    }



    @GetMapping("/admin_page")
    @PreAuthorize("hasRole('ADMIN')")
    public String getAdminPage(Model model, Principal principal) {
        String username = principal.getName();
        auditService.logAction("ACCESS_ADMIN_PAGE", username, "Admin accessed the admin page");

        model.addAttribute("userLogin", usersService.getLoggedInUserLogin());

        List<Movie> movies = movieService.getAllMovies();
        List<UsersModel> registeredUsers = usersService.getAllUsers();
        List<AuditLog> auditLogs = auditLogRepository.findAll();  // Fetch audit logs

        model.addAttribute("users", registeredUsers);
        model.addAttribute("movies", movies);
        model.addAttribute("auditLogs", auditLogs);  // Pass audit logs to the view

        return "admin_page";
    }




    @GetMapping("/personal_page")
    public String getPersonalPage(Model model, Principal principal) {
        String username = (principal != null) ? principal.getName() : "Guest";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Log action in the audit trail
        auditService.logAction("ACCESS_PERSONAL_PAGE", username, username + " accessed their personal page");

        if (principal != null) {
            String userRole = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(", "));

            model.addAttribute("userLogin", username);
            model.addAttribute("userRole", userRole);
        } else {
            model.addAttribute("userLogin", "Guest");
            model.addAttribute("userRole", null);
        }

        // Get all movies and recommended movies separately
        List<Movie> movies = movieService.getAllMovies();
        List<Movie> recommendedMovies = movieService.getRecommendedMovies();

        model.addAttribute("movies", movies);
        model.addAttribute("recommendedMovies", recommendedMovies);

        return "personal_page";
    }



@GetMapping("/forgot-password")
public String forgotPasswordRedirect() {
    // Redirect to PasswordResetController's method
    return "redirect:/password-reset/forgot-password";
}


    // Endpoint to create a new movie
    @PostMapping("/admin_page")
    public String createMovie(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam("imageFile") MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {
        try {
            // Save the image and create the movie
            String imageUrl = movieService.saveImage(imageFile);
            movieService.createMovie(name, description, imageUrl);

            // Add success message
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("movie.success.creation", null, LocaleContextHolder.getLocale()));

            // Redirect to prevent form resubmission
            return "redirect:/admin_page";

        } catch (Exception e) {
            // Add error message
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("movie.error.creation", null, LocaleContextHolder.getLocale()));

            // Log the error
            log.error("Error creating movie: ", e);

            // Redirect back to admin page with error message
            return "redirect:/admin_page";
        }
    }

}
