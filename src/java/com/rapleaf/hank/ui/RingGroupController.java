package com.rapleaf.hank.ui;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.rapleaf.hank.coordinator.Coordinator;
import com.rapleaf.hank.coordinator.RingGroupConfig;
import com.rapleaf.hank.exception.DataNotFoundException;
import com.rapleaf.hank.ui.controller.Action;
import com.rapleaf.hank.ui.controller.Controller;

public class RingGroupController extends Controller {

  private final Coordinator coordinator;

  public RingGroupController(String name, Coordinator coordinator) {
    super(name);
    this.coordinator = coordinator;

    actions.put("create", new Action() {
      @Override
      protected void action(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doCreate(req, resp);
      }
    });
    actions.put("add_ring", new Action() {
      @Override
      protected void action(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doAddRing(req, resp);
      }
    });
  }

  private void doAddRing(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    RingGroupConfig ringGroupConfig;
    String encodedRingGroupName = req.getParameter("g");
    try {
      ringGroupConfig = coordinator.getRingGroupConfig(URLDecoder.decode(encodedRingGroupName));
      if (ringGroupConfig == null) {
        throw new IOException("couldn't find any ring group called " + URLDecoder.decode(encodedRingGroupName));
      }
    } catch (DataNotFoundException e) {
      throw new IOException(e);
    }
    ringGroupConfig.addRing(ringGroupConfig.getRingConfigs().size() + 1);
    resp.sendRedirect("/ring_group.jsp?name=" + encodedRingGroupName);
  }

  private void doCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    coordinator.addRingGroup(req.getParameter("rgName"), req.getParameter("dgName"));
    // could log the rg...
    resp.sendRedirect("/ring_groups.jsp");
  }
}