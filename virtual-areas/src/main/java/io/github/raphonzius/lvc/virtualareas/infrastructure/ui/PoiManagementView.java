package io.github.raphonzius.lvc.virtualareas.infrastructure.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
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
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreatePoiRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.PoiResponse;

@Route(value = "pois", layout = MainLayout.class)
@PageTitle("Points of Interest — Virtual Areas")
@AnonymousAllowed
public class PoiManagementView extends VerticalLayout {

    private final AreaManagementService service;
    private final Grid<PoiResponse> grid = new Grid<>(PoiResponse.class, false);

    public PoiManagementView(AreaManagementService service) {
        this.service = service;
        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Gap.MEDIUM);

        configureGrid();
        add(header(), grid);
        refresh();
    }

    private HorizontalLayout header() {
        H2 title = new H2("Points of Interest");
        title.addClassNames(LumoUtility.Margin.NONE);

        Button add = new Button("New POI", e -> openForm(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout header = new HorizontalLayout(title, add);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return header;
    }

    private void configureGrid() {
        grid.addColumn(PoiResponse::name).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(PoiResponse::type).setHeader("Type").setAutoWidth(true);
        grid.addColumn(r -> String.format("%.6f, %.6f", r.lat(), r.lon())).setHeader("Coordinates").setAutoWidth(true);
        grid.addColumn(PoiResponse::description).setHeader("Description").setAutoWidth(true);
        grid.addComponentColumn(r -> {
            Button edit = new Button("Edit", e -> openForm(r));
            Button delete = new Button("Delete", e -> {
                service.deletePoi(r.id());
                refresh();
            });
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            HorizontalLayout actions = new HorizontalLayout(edit, delete);
            actions.setSpacing(false);
            actions.addClassNames(LumoUtility.Gap.XSMALL);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
    }

    private void openForm(PoiResponse existing) {
        Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.setHeaderTitle(existing == null ? "New POI" : "Edit POI");

        TextField name = new TextField("Name");
        TextField description = new TextField("Description");
        Select<String> type = new Select<>();
        type.setLabel("Type");
        type.setItems("BUS_STOP", "MISSION_TARGET", "GENERIC");
        NumberField lat = new NumberField("Latitude");
        NumberField lon = new NumberField("Longitude");
        NumberField alt = new NumberField("Altitude (m, optional)");
        TextArea metadata = new TextArea("Metadata (JSON)");

        if (existing != null) {
            name.setValue(existing.name() != null ? existing.name() : "");
            description.setValue(existing.description() != null ? existing.description() : "");
            type.setValue(existing.type());
            lat.setValue(existing.lat());
            lon.setValue(existing.lon());
            if (existing.altitudeM() != null) alt.setValue(existing.altitudeM());
            if (existing.metadata() != null) metadata.setValue(existing.metadata());
        }

        Button save = new Button("Save", e -> {
            try {
                var req = new CreatePoiRequest(name.getValue(), description.getValue(), type.getValue(),
                        lat.getValue(), lon.getValue(), alt.getValue(), metadata.getValue());
                if (existing == null) service.createPoi(req);
                else service.updatePoi(existing.id(), req);
                dialog.close();
                refresh();
                Notification n = Notification.show("Saved", 2000, Notification.Position.BOTTOM_END);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 4000, Notification.Position.BOTTOM_END);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new FormLayout(name, description, type, lat, lon, alt, metadata));
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void refresh() {
        grid.setItems(service.getAllPois().stream().map(PoiResponse::from).toList());
    }
}
