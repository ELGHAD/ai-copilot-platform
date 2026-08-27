package com.alten.chat_service.config;

import com.alten.chat_service.model.TicketType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class TicketCategorySchemas {

    public record FieldRule(String key, String label, boolean required, String type, String placeholder) {}

    private final Map<TicketType, List<FieldRule>> schemas = Map.of(

            TicketType.FORM_SOFTWARE_INSTALL, List.of(
                    new FieldRule("departmentProjectTeam", "Department / Project / Team", false, "text", null),
                    new FieldRule("approverName", "Approver's name (DT/FO)", false, "text", null),
                    new FieldRule("softwareFullName", "Full name of the requested software", true, "text", null),
                    new FieldRule("installationSource", "Installation source (download link)", false, "text", null),
                    new FieldRule("softwarePublisher", "Software publisher or vendor", false, "text", null),
                    new FieldRule("exactVersionRequired", "Exact version required", false, "text", null),
                    new FieldRule("businessJustification", "Business justification / professional need", false, "textarea", null),
                    new FieldRule("primaryPurpose", "Primary purpose", false, "text", "collaboration, development, design..."),
                    new FieldRule("adminPrivilegeRequired", "Administrator privilege required for execution", false, "checkbox", null)
            ),

            TicketType.FORM_ACCESS_REQUEST, List.of(
                    new FieldRule("application", "Application concernée", true, "text", null),
                    new FieldRule("accessType", "Type d'accès demandé", true, "text", null),
                    new FieldRule("managerApprover", "Nom du manager approbateur", false, "text", null),
                    new FieldRule("duration", "Durée (permanent / temporaire)", false, "text", null)
            ),

            TicketType.FORM_HARDWARE_ISSUE, List.of(
                    new FieldRule("equipmentType", "Type d'équipement", true, "text", "PC, écran, imprimante..."),
                    new FieldRule("assetNumber", "Numéro de série / asset", false, "text", null),
                    new FieldRule("issueDescription", "Description de la panne", true, "textarea", null),
                    new FieldRule("urgency", "Urgence", false, "text", null)
            ),

            TicketType.FORM_NETWORK_VPN, List.of(
                    new FieldRule("connectionType", "Type de connexion", true, "text", "VPN, WiFi, filaire..."),
                    new FieldRule("location", "Localisation", false, "text", null),
                    new FieldRule("errorMessage", "Message d'erreur", false, "textarea", null)
            ),

            TicketType.FORM_USER_ACCOUNT, List.of(
                    new FieldRule("actionType", "Création / Modification / Suppression", true, "text", null),
                    new FieldRule("targetUser", "Nom du user concerné", true, "text", null),
                    new FieldRule("department", "Département", false, "text", null),
                    new FieldRule("requestedRole", "Rôle demandé", false, "text", null)
            )
    );

    public List<FieldRule> getSchema(TicketType type) {
        return schemas.getOrDefault(type, List.of());
    }

    public Map<TicketType, List<FieldRule>> getAllSchemas() {
        return schemas;
    }
}