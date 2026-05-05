package io.github.raphonzius.lvc.virtualareas.infrastructure.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityState;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityStatePort;
import io.github.raphonzius.lvc.virtualareas.domain.event.InfluxProjectionPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Route(value = "monitoring", layout = MainLayout.class)
@PageTitle("Monitoring — Virtual Areas")
@AnonymousAllowed
public class MonitoringView extends VerticalLayout {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("America/Sao_Paulo"));
    private static final int TICKS_PER_REFRESH = 100;

    private final EntityStatePort entityStatePort;
    private final InfluxProjectionPort influxPort;

    private final Grid<EntityState> stateGrid = new Grid<>(EntityState.class, false);
    private final Grid<TransitionRecord> eventGrid = new Grid<>(TransitionRecord.class, false);
    private final Span statusBadge = new Span("—");
    private final ProgressBar refreshBar = new ProgressBar(0, 1, 0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger tick = new AtomicInteger(0);
    private ScheduledFuture<?> future;

    public MonitoringView(EntityStatePort entityStatePort, InfluxProjectionPort influxPort) {
        this.entityStatePort = entityStatePort;
        this.influxPort = influxPort;
        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Gap.LARGE);

        refreshBar.setWidthFull();
        refreshBar.getStyle().set("height", "4px");

        configureStateGrid();
        configureEventGrid();
        add(pageHeader(), refreshBar,
                sectionTitle("Entities inside areas"), stateGrid,
                sectionTitle("Recent transitions (last 24h)"), eventGrid);
    }

    private HorizontalLayout pageHeader() {
        H2 title = new H2("Monitoring");
        title.addClassNames(LumoUtility.Margin.NONE);
        statusBadge.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Button refreshBtn = new Button(VaadinIcon.REFRESH.create(),
                e -> manualRefresh(e.getSource().getUI().orElse(null)));
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        refreshBtn.setTooltipText("Refresh now");

        StreamResource excelResource = new StreamResource("monitoring-export.xlsx", this::generateExcel);
        Anchor exportAnchor = new Anchor(excelResource, "");
        exportAnchor.getElement().setAttribute("download", true);
        Button exportBtn = new Button(VaadinIcon.TABLE.create());
        exportBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        exportBtn.setTooltipText("Export Excel");
        exportAnchor.add(exportBtn);

        HorizontalLayout header = new HorizontalLayout(title, statusBadge, refreshBtn, exportAnchor);
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
        // Tick every 1s: advance bar 10% per tick; refresh at 100%
        future = scheduler.scheduleAtFixedRate(() -> {
            int t = tick.incrementAndGet();
            double progress = Math.min(1.0, (double) t / TICKS_PER_REFRESH);
            ui.access(() -> refreshBar.setValue(progress));
            if (t >= TICKS_PER_REFRESH) {
                tick.set(0);
                fetchExecutor.submit(() -> fetchAndUpdate(ui));
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        fetchExecutor.submit(() -> fetchAndUpdate(ui));
    }

    @Override
    protected void onDetach(DetachEvent e) {
        if (future != null) future.cancel(false);
    }

    private void manualRefresh(UI ui) {
        if (ui == null) return;
        tick.set(0);
        fetchExecutor.submit(() -> fetchAndUpdate(ui));
    }

    private void fetchAndUpdate(UI ui) {
        try {
            List<EntityState> states = entityStatePort.findAll();
            List<TransitionRecord> events = influxPort.queryRecent(50);
            ui.access(() -> {
                stateGrid.setItems(states);
                eventGrid.setItems(events);
                statusBadge.setText("Updated " + FMT.format(Instant.now()));
                refreshBar.setValue(0);
            });
        } catch (Exception ex) {
            ui.access(() -> {
                statusBadge.setText("Error: " + ex.getMessage());
                refreshBar.setValue(0);
            });
        }
    }

    private InputStream generateExcel() {
        try (var wb = new XSSFWorkbook()) {
            var s1 = wb.createSheet("Entities inside areas");
            fillHeader(s1.createRow(0), "Entity ID", "Type", "Area ID", "Entered At", "Last Seen");
            List<EntityState> states = entityStatePort.findAll();
            for (int i = 0; i < states.size(); i++) {
                EntityState s = states.get(i);
                fillRow(s1.createRow(i + 1),
                        s.entityId(), s.entityType().name(), s.areaId(),
                        FMT.format(s.enteredAt()), FMT.format(s.lastSeenAt()));
            }

            var s2 = wb.createSheet("Recent transitions (24h)");
            fillHeader(s2.createRow(0), "Area", "Entity", "Type", "Transition", "LVC", "Time");
            List<TransitionRecord> events = influxPort.queryRecent(50);
            for (int i = 0; i < events.size(); i++) {
                TransitionRecord r = events.get(i);
                fillRow(s2.createRow(i + 1),
                        r.areaName(), r.entityId(), r.entityType().name(),
                        r.transition(), r.lvc().name(), FMT.format(r.timestamp()));
            }

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Excel generation failed", e);
        }
    }

    private void fillHeader(Row row, String... headers) {
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
    }

    private void fillRow(Row row, String... values) {
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private static void injectScrollbarStyle(Grid<?> grid) {
        grid.addAttachListener(e -> grid.getElement().executeJs("""
                const s = document.createElement('style');
                s.textContent = `
                    #scroller { scrollbar-width: thin; scrollbar-color: var(--lumo-contrast-30pct) transparent; }
                    #scroller::-webkit-scrollbar { width: 5px; height: 5px; }
                    #scroller::-webkit-scrollbar-track { background: transparent; }
                    #scroller::-webkit-scrollbar-thumb { background: var(--lumo-contrast-20pct); border-radius: 99px; }
                    #scroller::-webkit-scrollbar-thumb:hover { background: var(--lumo-contrast-50pct); }
                    #scroller::-webkit-scrollbar-corner { background: transparent; }
                `;
                this.shadowRoot.appendChild(s);
                """));
    }

    private void configureStateGrid() {
        stateGrid.addColumn(EntityState::entityId).setHeader("Entity ID").setAutoWidth(true).setFlexGrow(1);
        stateGrid.addColumn(s -> s.entityType().name()).setHeader("Type").setAutoWidth(true);
        stateGrid.addColumn(EntityState::areaId).setHeader("Area ID").setAutoWidth(true);
        stateGrid.addColumn(s -> FMT.format(s.enteredAt())).setHeader("Entered At").setAutoWidth(true);
        stateGrid.addColumn(s -> FMT.format(s.lastSeenAt())).setHeader("Last Seen").setAutoWidth(true);
        stateGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        stateGrid.setHeight("220px");
        injectScrollbarStyle(stateGrid);
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
        injectScrollbarStyle(eventGrid);
    }
}
