/* eslint import/no-webpack-loader-syntax: 0 */

import alert from '-!svg-react-loader!./alert.svg';
import alerts from '-!svg-react-loader!./alerts.svg';
import dashboard from '-!svg-react-loader!./dashboard.svg';
import dashboards from '-!svg-react-loader!./dashboards.svg';
import reportBar from '-!svg-react-loader!./report-bar-chart.svg';
import reportHeat from '-!svg-react-loader!./report-heatmap.svg';
import reportLine from '-!svg-react-loader!./report-line-chart.svg';
import reportNumber from '-!svg-react-loader!./report-number.svg';
import reportPie from '-!svg-react-loader!./report-pie-chart.svg';
import reportTable from '-!svg-react-loader!./report-table.svg';
import reports from '-!svg-react-loader!./reports.svg';
import reportEmpty from '-!svg-react-loader!./report-empty.svg';

const icons = {
  alert,
  alerts,
  reports,
  report: reportEmpty,
  dashboard,
  dashboards,
  reportBar: {label: 'Bar chart Report', Component: reportBar},
  reportHeat: {label: 'Heatmap Report', Component: reportHeat},
  reportLine: {label: 'Line chart Report', Component: reportLine},
  reportNumber: {label: 'Number Report', Component: reportNumber},
  reportPie: {label: 'Pie chart Report', Component: reportPie},
  reportTable: {label: 'Table Report', Component: reportTable}
};

export default icons;
