package com.grash;

import com.grash.repository.GeneralPreferencesRepository;
import com.grash.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class ApplicationInitializerTest {
    @Test
    void doesNotCreatePrivilegedAccountWithoutExplicitCredentials() {
        UserService users = mock(UserService.class);
        RoleService roles = mock(RoleService.class);
        CompanyService companies = mock(CompanyService.class);
        ApplicationInitializer initializer = new ApplicationInitializer(users, mock(UserInvitationService.class),
                mock(GeneralPreferencesRepository.class), roles, companies,
                mock(SubscriptionPlanService.class), mock(ScheduleService.class));
        ReflectionTestUtils.setField(initializer, "bootstrapSuperAdminEmail", "");
        ReflectionTestUtils.setField(initializer, "bootstrapSuperAdminPassword", "");
        ReflectionTestUtils.invokeMethod(initializer, "initializeSuperAdmin");
        verifyNoInteractions(users, roles, companies);
    }
}
