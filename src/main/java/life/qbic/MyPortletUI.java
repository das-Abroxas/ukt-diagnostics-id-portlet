package life.qbic;

import javax.portlet.PortletContext;
import javax.portlet.PortletSession;

import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.WrappedPortletSession;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import life.qbic.portal.liferayandvaadinhelpers.main.LiferayAndVaadinUtils;

@Theme("mytheme")
@SuppressWarnings("serial")
@Widgetset("life.qbic.AppWidgetSet")
public class MyPortletUI extends UI {

    private static Log log = LogFactoryUtil.getLog(MyPortletUI.class);

    private static Boolean testing = true;

    @Override
    protected void init(VaadinRequest request) {
        String portletContextName = "Testing";
        Integer numOfRegisteredUsers = 1;
        final VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        setContent(layout);

        final Button button = new Button("Test");

        String userID = "MISSING SCREENNAME";
        if (LiferayAndVaadinUtils.isLiferayPortlet()) {
            userID = LiferayAndVaadinUtils.getUser().getScreenName();
            portletContextName = getPortletContextName(request);
            numOfRegisteredUsers = getPortalCountOfRegisteredUsers();
        }
        Label label = new Label(
                "Hello, " + userID + "!<br>This is portlet "
                        + portletContextName
                        + ".<br>This portal has "
                        + numOfRegisteredUsers
                        + " registered users (according to the data returned by Liferay API call).",
                ContentMode.HTML);

        button.addClickListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                layout.addComponent(label);
            }
        });
        layout.addComponent(button);
    }

    private String getPortletContextName(VaadinRequest request) {
        WrappedPortletSession wrappedPortletSession = (WrappedPortletSession) request
                .getWrappedSession();
        PortletSession portletSession = wrappedPortletSession
                .getPortletSession();

        final PortletContext context = portletSession.getPortletContext();
        final String portletContextName = context.getPortletContextName();
        return portletContextName;
    }

    private Integer getPortalCountOfRegisteredUsers() {
        Integer result = null;

        try {
            result = UserLocalServiceUtil.getUsersCount();
        } catch (SystemException e) {
            log.error(e);
        }

        return result;
    }
}
