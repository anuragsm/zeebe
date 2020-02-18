/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as FlowNodeWorkFlow} from 'modules/components/Icon/flow-node-workflow-root.svg';

/**
 * Gateway Imports
 */
import {ReactComponent as FlowNodeGatewayExclusive} from 'modules/components/Icon/flow-node-gateway-exclusive.svg';
import {ReactComponent as FlowNodeGatewayParallel} from 'modules/components/Icon/flow-node-gateway-parallel.svg';
import {ReactComponent as FlowNodeGatewayEventBased} from 'modules/components/Icon/flow-node-gateway-event-based.svg';

/**
 * Task Imports
 */

import {ReactComponent as FlowNodeTask} from 'modules/components/Icon/flow-node-task-undefined.svg';
import {ReactComponent as FlowNodeTaskService} from 'modules/components/Icon/flow-node-task-service.svg';
import {ReactComponent as FlowNodeTaskReceive} from 'modules/components/Icon/flow-node-task-receive.svg';
import {ReactComponent as FlowNodeTaskSend} from 'modules/components/Icon/flow-node-task-send.svg';
import {ReactComponent as FlowNodeTaskSubProcess} from 'modules/components/Icon/flow-node-subprocess-embedded.svg';
import {ReactComponent as FlowNodeTaskMulti} from 'modules/components/Icon/flow-node-multi-instance-parallel.svg';
import {ReactComponent as FlowNodeTaskParallel} from 'modules/components/Icon/flow-node-multi-instance-sequential.svg';
import {ReactComponent as FlowNodeCallActivity} from 'modules/components/Icon/flow-node-call-activity.svg';

/**
 * Event Imports
 */

import {ReactComponent as FlowNodeEventStart} from 'modules/components/Icon/flow-node-event-start.svg';
import {ReactComponent as FlowNodeEventEnd} from 'modules/components/Icon/flow-node-event-end.svg';

import {ReactComponent as FlowNodeEventMessageStart} from 'modules/components/Icon/flow-node-event-message-start.svg';

import {ReactComponent as FlowNodeEventMessageIntermediateThrow} from 'modules/components/Icon/flow-node-event-message-throw.svg';
import {ReactComponent as FlowNodeEventMessageBoundaryNonInterrupting} from 'modules/components/Icon/flow-node-event-message-non-interrupting.svg';
import {ReactComponent as FlowNodeEventMessageBoundaryInterrupting} from 'modules/components/Icon/flow-node-event-message-interrupting.svg';
import {ReactComponent as FlowNodeEventMessageEnd} from 'modules/components/Icon/flow-node-event-message-end.svg';

import {ReactComponent as FlowNodeEventTimerStart} from 'modules/components/Icon/flow-node-event-timer-start.svg';
import {ReactComponent as FlowNodeEventTimerBoundaryInterrupting} from 'modules/components/Icon/flow-node-event-timer-interrupting.svg';
import {ReactComponent as FlowNodeEventTimerBoundaryNonInerrupting} from 'modules/components/Icon/flow-node-event-timer-non-interrupting.svg';

import {ReactComponent as FlowNodeEventErrorStart} from 'modules/components/Icon/flow-node-event-error-start.svg';
import {ReactComponent as FlowNodeEventErrorBoundary} from 'modules/components/Icon/flow-node-event-error-boundary.svg';
import {ReactComponent as FlowNodeEventErrorEnd} from 'modules/components/Icon/flow-node-event-error-end.svg';

import {ReactComponent as FlowNodeEventSubprocess} from 'modules/components/Icon/flow-node-subprocess-event.svg';

const newIconStyle = css`
  position: relative;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  top: 0px;
  left: 3px;
`;

export const WORKFLOW = themed(styled(FlowNodeWorkFlow)`
  ${newIconStyle}
`);

/**
 * Gateways
 */

export const GATEWAY_EXCLUSIVE = themed(styled(FlowNodeGatewayExclusive)`
  ${newIconStyle}
  top: 3px;
  left: 0px;
`);

export const GATEWAY_PARALLEL = themed(styled(FlowNodeGatewayParallel)`
  ${newIconStyle}
  top: 3px;
  left: 0px;
`);

export const GATEWAY_EVENT_BASED = themed(styled(FlowNodeGatewayEventBased)`
  ${newIconStyle}
  left: 3px;
`);

/**
 * Task Icons
 */

export const TASK_SERVICE = themed(styled(FlowNodeTaskService)`
  ${newIconStyle};
  top: 3px;
  left: 0px;
`);

export const TASK_RECEIVE = themed(styled(FlowNodeTaskReceive)`
  ${newIconStyle};
`);

export const TASK_SEND = themed(styled(FlowNodeTaskSend)`
  ${newIconStyle};
`);

export const TASK_DEFAULT = themed(styled(FlowNodeTask)`
  ${newIconStyle};
`);

export const TASK_SUBPROCESS = themed(styled(FlowNodeTaskSubProcess)`
  ${newIconStyle};
`);

export const TASK_CALL_ACTIVITY = themed(
  styled(FlowNodeCallActivity)`
    ${newIconStyle};
  `
);

/**
 * Multi Instance Icons
 */

export const MULTI_SEQUENTIAL = themed(styled(FlowNodeTaskMulti)`
  ${newIconStyle};
`);

export const MULTI_PARALLEL = themed(styled(FlowNodeTaskParallel)`
  ${newIconStyle};
`);

/**
 * Event Icons
 */

export const START = themed(styled(FlowNodeEventStart)`
  ${newIconStyle}
`);

export const END = themed(styled(FlowNodeEventEnd)`
  ${newIconStyle}
  padding: 1px;
`);

/**
 * Message Event Icons
 */

export const EVENT_MESSAGE_START = themed(styled(FlowNodeEventMessageStart)`
  ${newIconStyle}
`);

export const EVENT_MESSAGE_END = themed(styled(FlowNodeEventMessageEnd)`
  ${newIconStyle}
`);

/**
 * Error Event Icons
 */
export const EVENT_ERROR_START = themed(styled(FlowNodeEventErrorStart)`
  ${newIconStyle}
`);

export const EVENT_ERROR_BOUNDARY_INTERRUPTING = themed(styled(
  FlowNodeEventErrorBoundary
)`
  ${newIconStyle}
`);

export const EVENT_ERROR_END = themed(styled(FlowNodeEventErrorEnd)`
  ${newIconStyle}
`);

/**
 * Other Icons
 */

export const EVENT_SUBPROCESS = themed(styled(FlowNodeEventSubprocess)`
  ${newIconStyle}
`);

// uses the same style as bondary interrupting
export const EVENT_MESSAGE_INTERMEDIATE_CATCH = themed(styled(
  FlowNodeEventMessageBoundaryInterrupting
)`
  ${newIconStyle}
`);

export const EVENT_MESSAGE_INTERMEDIATE_THROW = themed(styled(
  FlowNodeEventMessageIntermediateThrow
)`
  ${newIconStyle}
`);

export const EVENT_MESSAGE_BOUNDARY_INTERRUPTING = themed(styled(
  FlowNodeEventMessageBoundaryInterrupting
)`
  ${newIconStyle}
`);

export const EVENT_MESSAGE_BOUNDARY_NON_INTERRUPTING = themed(styled(
  FlowNodeEventMessageBoundaryNonInterrupting
)`
  ${newIconStyle}
`);

/**
 * Timer Event Icons
 */

export const EVENT_TIMER_START = themed(styled(FlowNodeEventTimerStart)`
  ${newIconStyle}
`);

export const EVENT_TIMER_INTERMEDIATE_CATCH = themed(styled(
  FlowNodeEventTimerBoundaryInterrupting
)`
  ${newIconStyle}
`);

export const EVENT_TIMER_BOUNDARY_NON_INTERRUPTING = themed(styled(
  FlowNodeEventTimerBoundaryNonInerrupting
)`
  ${newIconStyle}
`);

export const EVENT_TIMER_BOUNDARY_INTERRUPTING = themed(styled(
  FlowNodeEventTimerBoundaryInterrupting
)`
  ${newIconStyle}
`);
