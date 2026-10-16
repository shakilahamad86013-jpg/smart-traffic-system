import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Fetches admin dashboard statistics and recent violations,
 * then forwards to admin-dashboard.jsp. No SQL inside JSP.
 */
@WebServlet("/AdminDashboardServlet")
public class AdminDashboardServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null
                || !"ADMIN".equals(session.getAttribute("role"))) {
            response.sendRedirect("login.jsp");
            return;
        }

        int totalVehicles = 0;
        int totalViolations = 0;
        int paidCount = 0;
        int unpaidCount = 0;
        double totalFine = 0;
        double collectedFine = 0;
        double pendingFine = 0;

        List<Object[]> recentViolations = new ArrayList<>();

        String vehicleCountSql = "SELECT COUNT(*) FROM vehicles";

        String violationStatsSql = "SELECT COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'Paid' THEN 1 ELSE 0 END) AS paid, " +
                "SUM(CASE WHEN status = 'Unpaid' THEN 1 ELSE 0 END) AS unpaid, " +
                "COALESCE(SUM(fine_amount), 0) AS totalFine, " +
                "COALESCE(SUM(CASE WHEN status = 'Paid' THEN fine_amount ELSE 0 END), 0) AS collectedFine, " +
                "COALESCE(SUM(CASE WHEN status = 'Unpaid' THEN fine_amount ELSE 0 END), 0) AS pendingFine " +
                "FROM violations";

        String recentViolationsSql = "SELECT ve.vehicle_number, v.violation_type, v.violation_date, " +
                "v.fine_amount, v.status " +
                "FROM violations v " +
                "JOIN vehicles ve ON v.vehicle_id = ve.vehicle_id " +
                "ORDER BY v.violation_date DESC LIMIT 10";

        try (Connection con = DBConnection.getConnection()) {

            try (PreparedStatement ps = con.prepareStatement(vehicleCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalVehicles = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement(violationStatsSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalViolations = rs.getInt("total");
                    paidCount = rs.getInt("paid");
                    unpaidCount = rs.getInt("unpaid");
                    totalFine = rs.getDouble("totalFine");
                    collectedFine = rs.getDouble("collectedFine");
                    pendingFine = rs.getDouble("pendingFine");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(recentViolationsSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recentViolations.add(new Object[]{
                            rs.getString("vehicle_number"),
                            rs.getString("violation_type"),
                            rs.getDate("violation_date"),
                            String.format(Locale.US, "%,.2f", rs.getDouble("fine_amount")),
                            rs.getString("status")
                    });
                }
            }

        } catch (SQLException e) {
            throw new ServletException("Database error while loading admin dashboard", e);
        }

        request.setAttribute("totalVehicles", totalVehicles);
        request.setAttribute("totalViolations", totalViolations);
        request.setAttribute("paidCount", paidCount);
        request.setAttribute("unpaidCount", unpaidCount);
        request.setAttribute("totalFineAmount", String.format(Locale.US, "%,.0f", totalFine));
        request.setAttribute("collectedAmount", String.format(Locale.US, "%,.0f", collectedFine));
        request.setAttribute("pendingAmount", String.format(Locale.US, "%,.0f", pendingFine));
        request.setAttribute("recentViolations", recentViolations);

        request.getRequestDispatcher("admin-dashboard.jsp").forward(request, response);
    }
}