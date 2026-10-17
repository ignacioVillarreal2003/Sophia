package com.sophia.documentworkspace.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceTemplate;
import com.sophia.documentworkspace.domain.util.SpreadsheetCodec;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Fills a brand new workspace with the starting structure of the template the owner picked. */
@Service
@RequiredArgsConstructor
public class WorkspaceStarterService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ArtifactRepository artifactRepository;
    private final DocumentContentService contentService;

    public void apply(Workspace workspace, UUID ownerId, WorkspaceTemplate template) {
        if (template == null || template == WorkspaceTemplate.BLANK) {
            return;
        }
        Starter starter = new Starter(workspace, ownerId, workspace.getLanguage() != null
                && workspace.getLanguage().toLowerCase().startsWith("en"));
        switch (template) {
            case PROJECT -> starter.project();
            case TEAM -> starter.team();
            case PERSONAL -> starter.personal();
            default -> {
            }
        }
    }

    private final class Starter {
        private final Workspace workspace;
        private final UUID owner;
        private final boolean english;

        Starter(Workspace workspace, UUID owner, boolean english) {
            this.workspace = workspace;
            this.owner = owner;
            this.english = english;
        }

        private String t(String es, String en) {
            return english ? en : es;
        }

        private Folder folder(Folder parent, String name, String description, String color) {
            return (Folder) artifactRepository.save(
                    Folder.create(workspace.getId(), parent, name, owner, description, color));
        }

        private void doc(Folder parent, ArtifactType type, String name, String content) {
            DocumentArtifact document = DocumentArtifact.create(type, workspace.getId(), parent, name, owner, null, content);
            document = (DocumentArtifact) artifactRepository.save(document);
            contentService.replaceContent(document, content, owner);
        }

        private String sheet(String[][] rows, int[] widths) {
            ObjectNode book = (ObjectNode) readTree(SpreadsheetCodec.emptyWorkbook());
            ObjectNode sheet = (ObjectNode) book.withArray("sheets").get(0);
            ObjectNode cells = (ObjectNode) sheet.get("cells");
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] == null || rows[r][c].isEmpty()) {
                        continue;
                    }
                    ObjectNode cell = cells.putObject(String.valueOf((char) ('A' + c)) + (r + 1));
                    String value = rows[r][c];
                    if (value.startsWith("=")) {
                        cell.put("f", value);
                    } else {
                        cell.put("v", value);
                    }
                    if (r == 0) {
                        ObjectNode style = cell.putObject("s");
                        style.put("b", true);
                        style.put("bg", "#eef2ff");
                    }
                }
            }
            ObjectNode widthNode = sheet.putObject("colWidths");
            for (int i = 0; i < widths.length; i++) {
                widthNode.put(String.valueOf(i), widths[i]);
            }
            return book.toString();
        }

        private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
            try {
                return JSON.readTree(json);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid starter workbook", e);
            }
        }

        void project() {
            Folder planning = folder(null, t("01 Planificación", "01 Planning"), null, "#6366F1");
            Folder delivery = folder(null, t("02 Ejecución", "02 Delivery"), null, "#0C8599");
            folder(null, t("03 Entregables", "03 Deliverables"), null, "#1A9D5B");
            doc(null, ArtifactType.MARKDOWN_DOCUMENT, t("Bienvenida", "Welcome"), t(
                    "# Bienvenida\n\nEste espacio reúne todo lo del proyecto.\n\n- **01 Planificación**: objetivos, alcance y hoja de ruta.\n- **02 Ejecución**: seguimiento de tareas y reuniones.\n- **03 Entregables**: lo que se entrega.\n\n> [!tip] Consejo\n> Escribí `/` en cualquier documento para ver los comandos y `[[` para enlazar otra nota.\n",
                    "# Welcome\n\nThis space holds everything about the project.\n\n- **01 Planning**: goals, scope and roadmap.\n- **02 Delivery**: task tracking and meetings.\n- **03 Deliverables**: what gets handed over.\n\n> [!tip] Tip\n> Type `/` in any document for commands and `[[` to link another note.\n"));
            doc(planning, ArtifactType.MARKDOWN_DOCUMENT, t("Resumen del proyecto", "Project summary"), t(
                    "# Resumen del proyecto\n\n> [!info] En una frase\n> Qué problema resuelve y para quién.\n\n## Objetivos\n\n- \n\n## Alcance\n\n| Incluido | Fuera de alcance |\n| --- | --- |\n|  |  |\n\n## Riesgos\n\n> [!warning] Riesgos\n> \n",
                    "# Project summary\n\n> [!info] In one sentence\n> What problem it solves and for whom.\n\n## Goals\n\n- \n\n## Scope\n\n| In scope | Out of scope |\n| --- | --- |\n|  |  |\n\n## Risks\n\n> [!warning] Risks\n> \n"));
            doc(planning, ArtifactType.CHECKLIST, t("Hoja de ruta", "Roadmap"), t(
                    "## Hitos\n\n- [ ] Kick-off\n- [ ] Primera entrega\n- [ ] Revisión con el cliente\n- [ ] Lanzamiento\n",
                    "## Milestones\n\n- [ ] Kick-off\n- [ ] First delivery\n- [ ] Client review\n- [ ] Launch\n"));
            doc(delivery, ArtifactType.SPREADSHEET, t("Seguimiento de tareas", "Task tracker"), sheet(new String[][]{
                    {t("Tarea", "Task"), t("Responsable", "Owner"), t("Estado", "Status"), t("Vence", "Due")},
                    {t("Definir alcance", "Define scope"), "", t("En curso", "In progress"), ""},
                    {t("Armar el plan", "Build the plan"), "", t("Pendiente", "To do"), ""},
                    {"", "", "", ""},
                    {t("Completadas", "Done"), "=COUNTIF(C2:C200,\"" + t("Hecho", "Done") + "\")"},
                    {t("Total", "Total"), "=COUNTA(A2:A200)"},
            }, new int[]{240, 140, 120, 110}));
            doc(delivery, ArtifactType.MARKDOWN_DOCUMENT, t("Reuniones", "Meetings"), t(
                    "# Reuniones\n\nUna sección por reunión, la más reciente arriba.\n\n## Reunión semanal\n\n**Asistentes:** \n\n### Temas\n\n1. \n\n### Acuerdos\n\n- [ ] \n",
                    "# Meetings\n\nOne section per meeting, newest first.\n\n## Weekly sync\n\n**Attendees:** \n\n### Topics\n\n1. \n\n### Decisions\n\n- [ ] \n"));
        }

        void team() {
            Folder team = folder(null, t("Equipo", "Team"), null, "#6366F1");
            Folder meetings = folder(null, t("Reuniones", "Meetings"), null, "#D98E04");
            Folder resources = folder(null, t("Recursos", "Resources"), null, "#1A9D5B");
            doc(null, ArtifactType.MARKDOWN_DOCUMENT, t("Bienvenida al equipo", "Welcome to the team"), t(
                    "# Bienvenida al equipo\n\nAcá encontrás cómo trabajamos, quién hace qué y dónde está cada cosa.\n\n- [[Acuerdos del equipo]]\n- [[Roles y responsabilidades]]\n- [[Acta semanal]]\n",
                    "# Welcome to the team\n\nHere is how we work, who does what and where things live.\n\n- [[Team agreements]]\n- [[Roles and responsibilities]]\n- [[Weekly notes]]\n"));
            doc(team, ArtifactType.MARKDOWN_DOCUMENT, t("Acuerdos del equipo", "Team agreements"), t(
                    "# Acuerdos del equipo\n\n## Cómo nos comunicamos\n\n- \n\n## Cómo decidimos\n\n- \n\n## Horarios\n\n- \n",
                    "# Team agreements\n\n## How we communicate\n\n- \n\n## How we decide\n\n- \n\n## Working hours\n\n- \n"));
            doc(team, ArtifactType.SPREADSHEET, t("Roles y responsabilidades", "Roles and responsibilities"), sheet(new String[][]{
                    {t("Persona", "Person"), t("Rol", "Role"), t("Responsable de", "Responsible for"), t("Suplente", "Backup")},
                    {"", "", "", ""},
            }, new int[]{180, 160, 280, 160}));
            doc(meetings, ArtifactType.MARKDOWN_DOCUMENT, t("Acta semanal", "Weekly notes"), t(
                    "# Acta semanal\n\n**Fecha:** \n**Asistentes:** \n\n## Agenda\n\n1. \n\n## Notas\n\n\n## Acciones\n\n- [ ] \n",
                    "# Weekly notes\n\n**Date:** \n**Attendees:** \n\n## Agenda\n\n1. \n\n## Notes\n\n\n## Actions\n\n- [ ] \n"));
            doc(meetings, ArtifactType.CHECKLIST, t("Acciones pendientes", "Open actions"), "- [ ] \n");
            doc(resources, ArtifactType.MARKDOWN_DOCUMENT, t("Enlaces útiles", "Useful links"), t(
                    "# Enlaces útiles\n\n- [Documentación](https://)\n- [Tablero](https://)\n",
                    "# Useful links\n\n- [Documentation](https://)\n- [Board](https://)\n"));
        }

        void personal() {
            Folder ideas = folder(null, t("Ideas", "Ideas"), null, "#F59F00");
            doc(null, ArtifactType.MARKDOWN_DOCUMENT, t("Notas rápidas", "Quick notes"), t(
                    "# Notas rápidas\n\nAnotá lo que se te ocurra. Más tarde lo ordenás.\n\n- \n",
                    "# Quick notes\n\nJot down whatever comes to mind. Sort it out later.\n\n- \n"));
            doc(null, ArtifactType.CHECKLIST, t("Pendientes", "To do"), t(
                    "## Hoy\n\n- [ ] \n\n## Esta semana\n\n- [ ] \n\n## Algún día\n\n- [ ] \n",
                    "## Today\n\n- [ ] \n\n## This week\n\n- [ ] \n\n## Someday\n\n- [ ] \n"));
            doc(null, ArtifactType.MARKDOWN_DOCUMENT, t("Diario", "Journal"), t(
                    "# Diario\n\n## Hoy\n\n### Lo mejor del día\n\n- \n\n### Para mañana\n\n- [ ] \n",
                    "# Journal\n\n## Today\n\n### Best part of the day\n\n- \n\n### For tomorrow\n\n- [ ] \n"));
            doc(ideas, ArtifactType.MARKDOWN_DOCUMENT, t("Banco de ideas", "Idea bank"), t(
                    "# Banco de ideas\n\n- \n", "# Idea bank\n\n- \n"));
        }
    }
}
