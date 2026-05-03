package io.github.raphonzius.lvc.virtualareas.infrastructure.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.application.service.EventSimulationService;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.AreaResponse;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.TransitionResult;

@Route(value = "simulation", layout = MainLayout.class)
@PageTitle("Simulation — Virtual Areas")
@AnonymousAllowed
public class SimulationView extends VerticalLayout {

    private final EventSimulationService simulationService;
    private final AreaManagementService areaService;

    private final Grid<TransitionResult> resultGrid = new Grid<>(TransitionResult.class, false);

    public SimulationView(EventSimulationService simulationService, AreaManagementService areaService) {
        this.simulationService = simulationService;
        this.areaService = areaService;
        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Gap.LARGE);

        H2 title = new H2("Simulation");
        title.addClassNames(LumoUtility.Margin.NONE);

        add(title, card(buildMoveSection()), card(buildFloodSection()));
    }

    private Div card(VerticalLayout content) {
        Div card = new Div(content);
        card.addClassNames(
                LumoUtility.Background.CONTRAST_5,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Padding.LARGE);
        card.setWidthFull();
        return card;
    }

    private VerticalLayout buildMoveSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.addClassNames(LumoUtility.Gap.MEDIUM);

        H3 heading = new H3("Ping Virtual Move Event");
        heading.addClassNames(LumoUtility.Margin.NONE);

        TextField entityId = new TextField("Entity ID");
        entityId.setPlaceholder("e.g. vehicle-001");

        Select<String> entityType = new Select<>();
        entityType.setLabel("Entity Type");
        entityType.setItems("DRONE", "BUS", "VESSEL");
        entityType.setValue("BUS");

        NumberField lat = new NumberField("Latitude");
        NumberField lon = new NumberField("Longitude");
        NumberField alt = new NumberField("Altitude m (optional)");

        Select<String> lvc = new Select<>();
        lvc.setLabel("LVC Origin");
        lvc.setItems("LIVE", "VIRTUAL", "CONSTRUCTIVE");
        lvc.setValue("VIRTUAL");

        Button send = new Button("Send Move Event", e -> handleMove(
                entityId.getValue(), entityType.getValue(),
                lat.getValue(), lon.getValue(), alt.getValue(), lvc.getValue()));
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        configureResultGrid();

        section.add(heading, new FormLayout(entityId, entityType, lat, lon, alt, lvc), send, resultGrid);
        return section;
    }

    private void configureResultGrid() {
        resultGrid.addColumn(TransitionResult::areaName).setHeader("Area").setAutoWidth(true);
        resultGrid.addColumn(TransitionResult::entityId).setHeader("Entity").setAutoWidth(true);
        resultGrid.addColumn(TransitionResult::transition).setHeader("Transition").setAutoWidth(true);
        resultGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        resultGrid.setHeight("180px");
        resultGrid.setVisible(false);
    }

    private void handleMove(String entityId, String type, Double lat, Double lon, Double alt, String lvc) {
        try {
            var results = simulationService
                    .pingMoveEvent(entityId, type, lat, lon, alt, lvc)
                    .stream().map(TransitionResult::from).toList();
            resultGrid.setItems(results);
            resultGrid.setVisible(true);
            String msg = results.isEmpty()
                    ? "Move sent — no area transitions"
                    : "Move sent — " + results.size() + " transition(s)";
            notify(msg, false);
        } catch (Exception ex) {
            notify("Error: " + ex.getMessage(), true);
        }
    }

    private VerticalLayout buildFloodSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.addClassNames(LumoUtility.Gap.MEDIUM);

        H3 heading = new H3("Simulate Virtual Flood Event");
        heading.addClassNames(LumoUtility.Margin.NONE);

        var areas = areaService.getAllAreas().stream().map(AreaResponse::from).toList();
        Select<AreaResponse> areaSelect = new Select<>();
        areaSelect.setLabel("Area");
        areaSelect.setItems(areas);
        areaSelect.setItemLabelGenerator(AreaResponse::name);

        Select<String> severity = new Select<>();
        severity.setLabel("Severity");
        severity.setItems("leve", "moderado", "grave");
        severity.setValue("moderado");

        NumberField waterLevel = new NumberField("Water Level (cm)");
        waterLevel.setValue(50.0);

        Select<String> lvc = new Select<>();
        lvc.setLabel("LVC Origin");
        lvc.setItems("LIVE", "VIRTUAL", "CONSTRUCTIVE");
        lvc.setValue("VIRTUAL");

        Button send = new Button("Simulate Flood", e -> {
            if (areaSelect.getValue() == null) {
                notify("Select an area", true);
                return;
            }
            try {
                simulationService.simulateFloodEvent(
                        areaSelect.getValue().id(), severity.getValue(),
                        waterLevel.getValue(), lvc.getValue());
                notify("Flood event sent to event-hub", false);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), true);
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_ERROR);

        section.add(heading, new FormLayout(areaSelect, severity, waterLevel, lvc), send);
        return section;
    }

    private void notify(String msg, boolean error) {
        Notification n = Notification.show(msg, 4000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(error ? NotificationVariant.LUMO_ERROR : NotificationVariant.LUMO_SUCCESS);
    }
}
