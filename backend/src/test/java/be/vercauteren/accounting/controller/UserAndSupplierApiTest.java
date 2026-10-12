package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.dto.SupplierRequest;
import be.vercauteren.accounting.dto.UserRequest;
import be.vercauteren.accounting.dto.UserUpdateRequest;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.service.UserService;
import be.vercauteren.accounting.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Gestion des comptes (reservee aux administrateurs) et des fiches fournisseurs. */
class UserAndSupplierApiTest extends IntegrationTest {

    @Nested
    class Users {

        private MvcResult createUser(UserRequest request) throws Exception {
            return mvc.perform(post("/api/users").with(as(admin())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andReturn();
        }

        @Test
        void anAdminManagesAccounts() throws Exception {
            MvcResult created = createUser(
                new UserRequest("carol", "carol@test.local", PASSWORD, UserRole.VIEWER, true));
            assertThat(created.getResponse().getStatus()).isEqualTo(201);
            long id = idOf(created);

            mvc.perform(get("/api/users").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'carol')]").exists());
            mvc.perform(get("/api/users/{id}", id).with(as(admin())))
                .andExpect(jsonPath("$.role").value("VIEWER"));

            mvc.perform(put("/api/users/{id}", id).with(as(admin())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new UserUpdateRequest("carol2@test.local", UserRole.USER, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol2@test.local"))
                .andExpect(jsonPath("$.enabled").value(false));

            mvc.perform(delete("/api/users/{id}", id).with(as(admin())).with(csrf()))
                .andExpect(status().isNoContent());
            assertThat(userRepository.findById(id)).isEmpty();

            mvc.perform(get("/api/users/{id}", id).with(as(admin())))
                .andExpect(status().isNotFound());
            mvc.perform(delete("/api/users/{id}", id).with(as(admin())).with(csrf()))
                .andExpect(status().isNotFound());
        }

        @Test
        void usernamesAndEmailsAreUnique() throws Exception {
            User dave = newUser("dave", UserRole.USER);
            newUser("erin", UserRole.USER);

            MvcResult sameName = createUser(new UserRequest("dave", "x@test.local", PASSWORD, UserRole.USER, true));
            assertThat(sameName.getResponse().getStatus()).isEqualTo(400);
            assertThat((String) read(sameName, "$.error")).isEqualTo("Username already exists");

            MvcResult sameEmail = createUser(
                new UserRequest("other", "dave@test.local", PASSWORD, UserRole.USER, true));
            assertThat((String) read(sameEmail, "$.error")).isEqualTo("Email already exists");

            mvc.perform(put("/api/users/{id}", dave.getId()).with(as(admin())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new UserUpdateRequest("erin@test.local", UserRole.USER, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already exists"));

            // Garder son propre email n'est pas un doublon.
            mvc.perform(put("/api/users/{id}", dave.getId()).with(as(admin())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new UserUpdateRequest("dave@test.local", UserRole.ADMIN, true))))
                .andExpect(status().isOk());
        }

        @Test
        void aWeakPasswordIsRefused() throws Exception {
            MvcResult result = createUser(new UserRequest("frank", "frank@test.local", "short", UserRole.USER, true));

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat((String) read(result, "$.errors.password")).isNotBlank();
        }

        @Test
        void theTechnicalUserCannotBeDeleted() throws Exception {
            User system = userRepository.findByUsername(UserService.SYSTEM_USERNAME).orElseThrow();

            mvc.perform(delete("/api/users/{id}", system.getId()).with(as(admin())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("technical user")));
        }

        @Test
        void onlyAdminsReachUserManagement() throws Exception {
            User user = newUser("gina", UserRole.USER);

            mvc.perform(get("/api/users").with(as(user))).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Suppliers {

        private SupplierRequest request(String name, ExpenseCategory category) {
            return new SupplierRequest(name, "Alias", "0456.789.034", category, DateScope.MONTHLY, true);
        }

        @Test
        void aUserManagesSuppliers() throws Exception {
            User user = newUser("hank", UserRole.USER);

            MvcResult created = mvc.perform(post("/api/suppliers").with(as(user)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request("Ondes", ExpenseCategory.TELECOM))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultPeppol").value(true))
                .andExpect(jsonPath("$.defaultDateScope").value("MONTHLY"))
                .andReturn();
            long id = idOf(created);

            mvc.perform(put("/api/suppliers/{id}", id).with(as(user)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request("Ondes SA", ExpenseCategory.TELECOM))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ondes SA"));

            mvc.perform(get("/api/suppliers/{id}", id).with(as(user)))
                .andExpect(jsonPath("$.alias").value("Alias"));

            mvc.perform(delete("/api/suppliers/{id}", id).with(as(user)).with(csrf()))
                .andExpect(status().isNoContent());
            mvc.perform(get("/api/suppliers/{id}", id).with(as(user)))
                .andExpect(status().isNotFound());
        }

        @Test
        void suppliersAreListedAlphabeticallyAndFilteredByCategory() throws Exception {
            supplier("zeta", null, null, ExpenseCategory.TELECOM);
            supplier("Alpha", null, null, ExpenseCategory.RESTAURANT);
            supplier("beta", null, null, ExpenseCategory.TELECOM);
            User viewer = newUser("ivy", UserRole.VIEWER);

            mvc.perform(get("/api/suppliers").with(as(viewer)))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("beta"))
                .andExpect(jsonPath("$[2].name").value("zeta"));

            mvc.perform(get("/api/suppliers").param("category", "TELECOM").with(as(viewer)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("beta"));
        }

        @Test
        void aSupplierWithInvoicesCannotBeDeleted() throws Exception {
            Supplier supplier = supplier("Used");
            save(invoiceOf(supplier, admin(), 2026, 1));

            mvc.perform(delete("/api/suppliers/{id}", supplier.getId()).with(as(admin())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("invoices reference")));
        }

        @Test
        void aNameIsRequired() throws Exception {
            mvc.perform(post("/api/suppliers").with(as(admin())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(" ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        }
    }
}
