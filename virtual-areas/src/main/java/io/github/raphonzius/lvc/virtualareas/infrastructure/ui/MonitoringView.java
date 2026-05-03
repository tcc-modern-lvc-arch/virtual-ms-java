package io.github.raphonzius.lvc.virtualareas.infrastructure.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityState;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityStatePort;
import io.github.raphonzius.lvc.virtualareas.domain.event.InfluxProjectionPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionRecord;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Route(value = "monitoring", layout = MainLayout.class)
@PageTitle("Monitoring — Virtual Areas")
@AnonymousAllowed
public class MonitoringView extends VerticalLayout {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("America/Sao_Paulo"));

    private final EntityStatePort entityStatePort;
    private final InfluxProjectionPort influxPort;

    private final Grid<EntityState> stateGrid = new Grid<>(EntityState.class, false);
    private final Grid<TransitionRecord> eventGrid = new Grid<>(TransitionRecord.class, false);
    private final Span statusBadge = new Span("—");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> future;

    public MonitoringView(EntityStatePort entityStatePort, InfluxProjectionPort influxPort) {
        this.entityStatePort = entityStatePort;
        this.influxPort = influxPort;
        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Gap.LARGE);

        configureStateGrid();
        configureEventGrid();
        add(pageHeader(), sectionTitle("Entities inside areas"), stateGrid,
                sectionTitle("Recent transitions (last 24h)"), eventGrid);
    }

    private HorizontalLayout pageHeader() {
        H2 title = new H2("Monitoring");
        title.addClassNames(LumoUtility.Margin.NONE);

        statusBadge.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Button refresh = new Button("Refresh", e -> refresh(getUI().orElse(null)));
        refresh.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(title, statusBadge, refresh);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setFlexGrow(1, title);
        return header;
    }

    private H3 sectionTitle(String text) {
        H3 h = new H3(text);
        h.addClassNames(LumoUtility.Margin.Bottom.NONE, LumoUtility.Margin.Top.NONE);
        return h;
    }

    @Override
    protected void onAttach(AttachEvent e) {
        UI ui = e.getUI();
        future = scheduler.scheduleAtFixedRate(
                () -> ui.access(() -> refresh(ui)), 0, 10, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent e) {
        if (future != null) future.cancel(false);
    }

    private void configureStateGrid() {
        stateGrid.addColumn(EntityState::entityId).setHeader("Entity ID").setAutoWidth(true).setFlexGrow(1);
        stateGrid.addColumn(s -> s.entityType().name()).setHeader("Type").setAutoWidth(true);
        stateGrid.addColumn(EntityState::areaId).setHeader("Area ID").setAutoWidth(true);
        stateGrid.addColumn(s -> FMT.format(s.enteredAt())).setHeader("Entered At").setAutoWidth(true);
        stateGrid.addColumn(s -> FMT.format(s.lastSeenAt())).setHeader("Last Seen").setAutoWidth(true);
        stateGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        stateGrid.setHeight("220px");
    }

    private void configureEventGrid() {
        eventGrid.addColumn(TransitionRecord::areaName).setHeader("Area").setAutoWidth(true).setFlexGrow(1);
        eventGrid.addColumn(TransitionRecord::entityId).setHeader("Entity").setAutoWidth(true);
        eventGrid.addColumn(r -> r.entityType().name()).setHeader("Type").setAutoWidth(true);
        eventGrid.addColumn(TransitionRecord::transition).setHeader("Transition").setAutoWidth(true);
        eventGrid.addColumn(r -> r.lvc().name()).setHeader("LVC").setAutoWidth(true);
        eventGrid.addColumn(r -> FMT.format(r.timestamp())).setHeader("Time").setAutoWidth(true);
        eventGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        eventGrid.setSizeFull();
    }

    private void refresh(UI ui) {
        try {
            var states = entityStatePort.findAll();
            var events = influxPort.queryRecent(50);
            stateGrid.setItems(states);
            eventGrid.setItems(events);
            statusBadge.setText("Updated " + FMT.format(java.time.Instant.now()));
        } catch (Exception e) {
            statusBadge.setText("Error: " + e.getMessage());
        }
    }
}
