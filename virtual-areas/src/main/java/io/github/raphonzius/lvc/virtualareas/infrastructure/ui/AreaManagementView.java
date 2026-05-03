package io.github.raphonzius.lvc.virtualareas.infrastructure.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.AreaResponse;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CoordinateDto;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreateAreaRequest;

import java.util.Arrays;
import java.util.List;

@Route(value = "areas", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Areas — Virtual Areas")
@AnonymousAllowed
public class AreaManagementView extends VerticalLayout {

    private final AreaManagementService service;
    private final Grid<AreaResponse> grid = new Grid<>(AreaResponse.class, false);

    public AreaManagementView(AreaManagementService service) {
        this.service = service;
        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Gap.MEDIUM);

        configureGrid();
        add(header(), grid);
        refresh();
    }

    private HorizontalLayout header() {
        H2 title = new H2("Areas");
        title.addClassNames(LumoUtility.Margin.NONE);

        Button addBtn = new Button("New Area", e -> openForm(null));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout header = new HorizontalLayout(title, addBtn);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return header;
    }

    private void configureGrid() {
        grid.addColumn(AreaResponse::name).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(AreaResponse::areaType).setHeader("Type").setAutoWidth(true);
        grid.addColumn(r -> String.join(", ", r.monitoredEntityTypes())).setHeader("Monitors").setAutoWidth(true);
        grid.addColumn(AreaResponse::action).setHeader("Action").setAutoWidth(true);
        grid.addComponentColumn(r -> {
            Span badge = new Span(r.active() ? "Active" : "Inactive");
            badge.getElement().getThemeList().add(r.active() ? "badge success" : "badge error");
            return badge;
        }).setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(r -> {
            Button edit = new Button("Edit", _ -> openForm(r));
            Button toggle = new Button(r.active() ? "Deactivate" : "Activate", _ -> toggleAndRefresh(r.id()));
            Button delete = new Button("Delete", _ -> deleteAndRefresh(r.id()));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            toggle.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            HorizontalLayout actions = new HorizontalLayout(edit, toggle, delete);
            actions.setSpacing(false);
            actions.addClassNames(LumoUtility.Gap.XSMALL);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
    }

    private void openForm(AreaResponse existing) {
        Dialog dialog = new Dialog();
        dialog.setWidth("700px");
        dialog.setHeaderTitle(existing == null ? "New Area" : "Edit Area");

        TextField name = new TextField("Name");
        TextField description = new TextField("Description");
        Select<String> areaType = new Select<>();
        areaType.setLabel("Type");
        areaType.setItems("POLYGON", "CIRCLE", "CORRIDOR");

        TextArea coordinates = new TextArea("Coordinates (JSON array of {lat,lon})");
        coordinates.setWidthFull();
        coordinates.setMinHeight("100px");
        coordinates.setPlaceholder("[{\"lat\":-23.55,\"lon\":-46.63}]");

        NumberField radius = new NumberField("Radius / Half-width (m)");
        radius.setHelperText("Required for CIRCLE and CORRIDOR");

        MultiSelectComboBox<String> entityTypes = new MultiSelectComboBox<>("Monitored Entity Types");
        entityTypes.setItems("DRONE", "BUS", "VESSEL", "FLOOD_AREA", "RAIN_AREA");

        Select<String> action = new Select<>();
        action.setLabel("Action");
        action.setItems("CHECKIN_CHECKOUT", "MISSION_TRIGGER");
        action.setValue("CHECKIN_CHECKOUT");

        List<PointOfInterest> allPois = service.getAllPois();
        ComboBox<PointOfInterest> targetPoi = new ComboBox<>("Mission Target POI");
        targetPoi.setItems(allPois);
        targetPoi.setItemLabelGenerator(PointOfInterest::name);
        targetPoi.setVisible(false);
        action.addValueChangeListener(e -> targetPoi.setVisible("MISSION_TRIGGER".equals(e.getValue())));

        Checkbox patrolZone = new Checkbox("Patrol Zone");
        patrolZone.setHelperText("Mark as drone patrol boundary (e.g. Mackenzie Campus)");

        if (existing != null) {
            name.setValue(existing.name() != null ? existing.name() : "");
            description.setValue(existing.description() != null ? existing.description() : "");
            areaType.setValue(existing.areaType());
            action.setValue(existing.action());
            entityTypes.setValue(existing.monitoredEntityTypes() != null
                    ? new java.util.HashSet<>(existing.monitoredEntityTypes()) : new java.util.HashSet<>());
            if (existing.radiusMeters() != null) radius.setValue(existing.radiusMeters());
            patrolZone.setValue(existing.patrolZone());
            if (existing.targetPoiId() != null) {
                allPois.stream().filter(p -> p.id().equals(existing.targetPoiId())).findFirst()
                        .ifPresent(targetPoi::setValue);
                targetPoi.setVisible(true);
            }
        }

        FormLayout form = new FormLayout(name, description, areaType, coordinates, radius, entityTypes, action, targetPoi, patrolZone);
        form.setColspan(coordinates, 2);

        Button save = new Button("Save", e -> {
            try {
                List<CoordinateDto> coords = parseCoords(coordinates.getValue());
                String poiId = targetPoi.getValue() != null ? targetPoi.getValue().id() : null;
                var req = new CreateAreaRequest(
                        name.getValue(), description.getValue(), areaType.getValue(),
                        coords, radius.getValue(), entityTypes.getValue().stream().toList(),
                        action.getValue(), poiId, patrolZone.getValue());
                if (existing == null) service.createArea(req);
                else service.updateArea(existing.id(), req);
                dialog.close();
                refresh();
                notify("Saved successfully", false);
            } catch (Exception ex) {
                notify("Error: " + ex.getMessage(), true);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", _ -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private List<CoordinateDto> parseCoords(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return Arrays.asList(mapper.readValue(json, CoordinateDto[].class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON coordinates: " + e.getMessage());
        }
    }

    private void toggleAndRefresh(String id) {
        service.toggleActive(id);
        refresh();
    }

    private void deleteAndRefresh(String id) {
        service.deleteArea(id);
        refresh();
        notify("Area deleted", false);
    }

    private void refresh() {
        grid.setItems(service.getAllAreas().stream().map(AreaResponse::from).toList());
    }

    private void notify(String msg, boolean error) {
        Notification n = Notification.show(msg, 3000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(error ? NotificationVariant.LUMO_ERROR : NotificationVariant.LUMO_SUCCESS);
    }
}
