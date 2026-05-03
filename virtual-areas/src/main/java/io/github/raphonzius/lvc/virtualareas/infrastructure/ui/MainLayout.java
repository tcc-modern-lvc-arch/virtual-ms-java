package io.github.raphonzius.lvc.virtualareas.infrastructure.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.lumo.LumoUtility;

@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout {

    private boolean dark = true;

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addToNavbar(new DrawerToggle(), title(), themeToggle());
        addToDrawer(nav());
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        event.getUI().getElement().getThemeList().add(Lumo.DARK);
    }

    private H1 title() {
        H1 h = new H1("Virtual Areas");
        h.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD);
        return h;
    }

    private Button themeToggle() {
        Button btn = new Button(VaadinIcon.ADJUST.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        btn.getStyle().set("margin-left", "auto").set("margin-right", "var(--lumo-space-m)");
        btn.addClickListener(e -> {
            dark = !dark;
            UI.getCurrent().getElement().getThemeList().set(Lumo.DARK, dark);
        });
        return btn;
    }

    private SideNav nav() {
        SideNav nav = new SideNav();
        nav.addClassNames(LumoUtility.Padding.SMALL);
        nav.setWidthFull();
        nav.addItem(new SideNavItem("Areas", AreaManagementView.class, VaadinIcon.MAP_MARKER.create()));
        nav.addItem(new SideNavItem("Points of Interest", PoiManagementView.class, VaadinIcon.LOCATION_ARROW.create()));
        nav.addItem(new SideNavItem("Simulation", SimulationView.class, VaadinIcon.PLAY.create()));
        nav.addItem(new SideNavItem("Monitoring", MonitoringView.class, VaadinIcon.DASHBOARD.create()));
        return nav;
    }
}
