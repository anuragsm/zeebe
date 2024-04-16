/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Variables} from './index';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as variableMocks from 'modules/mock-schema/mocks/variables';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import noop from 'lodash/noop';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {Variable} from 'modules/types';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

type VariableSearchRequestBody = {
  variableNames?: string[];
};

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

function isRequestingAllVariables(req: VariableSearchRequestBody) {
  return req.variableNames !== undefined && req.variableNames.length == 0;
}

describe('<Variables />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );

    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("should show an error message if variables can't be loaded", async () => {
    nodeMockServer.use(
      http.post('/v1/tasks/:taskId/variables/search', () => {
        return HttpResponse.json(null, {status: 404});
      }),
    );

    render(
      <Variables
        task={taskMocks.unassignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(
      screen.getByText(
        'We could not fetch the task variables. Please try again or contact your Tasklist administrator.',
      ),
    ).toBeInTheDocument();
  });

  it('should show existing variables for unassigned tasks', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.unassignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('"0001"')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('"yes"')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should show a message when the tasks has no variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json([]);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByText('Task has no variables'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('variables-form-table'),
    ).not.toBeInTheDocument();
  });

  it('should edit variable', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );
    const newVariableValue = '"changedValue"';

    expect(await screen.findByDisplayValue('"0001"')).toBeInTheDocument();

    await user.clear(screen.getByDisplayValue('"0001"'));
    await user.type(screen.getByLabelText('myVar'), newVariableValue);

    expect(screen.getByDisplayValue(newVariableValue)).toBeInTheDocument();

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
  });

  it('should add two variables and remove one', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.click(screen.getByText(/add variable/i));

    expect(screen.getAllByPlaceholderText(/^name$/i)).toHaveLength(2);
    expect(screen.getAllByPlaceholderText(/^value$/i)).toHaveLength(2);
    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/2nd variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/2nd variable value/i)).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();

    await user.click(screen.getByLabelText(/remove 2nd new variable/i));

    expect(screen.getAllByPlaceholderText(/^name$/i)).toHaveLength(1);
    expect(screen.getAllByPlaceholderText(/^value$/i)).toHaveLength(1);
    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();

    expect(
      screen.queryByLabelText(/2nd variable name/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/2nd variable value/i),
    ).not.toBeInTheDocument();
  });

  it('should add variable on task without variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));

    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should validate an empty variable name', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name has to be filled/i),
    );
  });

  it('should validate an invalid variable name', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), '"');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name is invalid/i),
    );

    await user.clear(screen.getByLabelText(/1st variable name/i));

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name is invalid/i),
    );

    await user.type(screen.getByLabelText(/1st variable name/i), 'test ');

    vi.runOnlyPendingTimers();

    expect(await screen.findByText(/name is invalid/i)).toBeInTheDocument();
  });

  it('should validate an empty variable value', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable value/i),
      ).toHaveAccessibleDescription(/value has to be json or a literal/i),
    );
  });

  it('should validate an invalid variable value', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));

    await user.type(
      screen.getByLabelText(/1st variable value/i),
      'invalid_value}}}',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name has to be filled/i),
    );
    expect(
      screen.getByLabelText(/1st variable value/i),
    ).toHaveAccessibleDescription(/value has to be json or a literal/i);
  });

  it('should not validate valid variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    expect(
      screen.queryByTitle(
        /name has to filled and value has to be json or a literal/i,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/name has to be filled/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/value has to be json or a literal/i),
    ).not.toBeInTheDocument();
  });

  it('should handle submission', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();
    const {rerender, user} = render(
      <Variables
        key="id_0"
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(await screen.findByText(/complete task/i));

    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(1);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(1, []);

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'var');
    await user.type(screen.getByLabelText(/1st variable value/i), '1');

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(2);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(2, [
      {
        name: 'var',
        value: '1',
      },
    ]);

    rerender(
      <Variables
        key="id_1"
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
    );

    expect(await screen.findByLabelText('myVar')).toBeInTheDocument();

    await user.click(await screen.findByText(/add variable/i));
    await user.type(screen.getByLabelText(/1st variable name/i), 'name');
    await user.type(screen.getByLabelText(/1st variable value/i), '"Jon"');

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(3);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(3, [
      {
        name: 'name',
        value: '"Jon"',
      },
    ]);
  });

  it('should change variable and complete task', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.clear(await screen.findByLabelText('myVar'));
    await user.type(screen.getByLabelText('myVar'), '"newValue"');
    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    await user.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'myVar',
          value: '"newValue"',
        },
      ]),
    );
  });

  it('should not be able to change variable, add variable and complete task if user has no permission', async () => {
    const UserName: React.FC = () => {
      const {data: currentUser} = useCurrentUser();

      return <div>{currentUser?.displayName}</div>;
    };

    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentRestrictedUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();

    render(
      <>
        <UserName />
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={mockOnSubmit}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />
      </>,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(await screen.findByText(/myVar/)).toBeInTheDocument();

    expect(screen.getByText(/add variable/i)).toBeDisabled();
    expect(screen.queryByLabelText('myVar')).not.toBeInTheDocument();
    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should add new variable and complete task', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const mockOnSubmit = vi.fn();

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(
      screen.getByLabelText(/1st variable name/i),
      'newVariableName',
    );
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"newVariableValue"',
    );

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
    await user.click(screen.getByText(/complete task/i));

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'newVariableName',
          value: '"newVariableValue"',
        },
      ]),
    );
  });

  it('should hide add variable button on completed tasks', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.completedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Variables')).toBeInTheDocument();

    expect(screen.queryByText(/add variable/i)).not.toBeInTheDocument();
  });

  it('should disable submit button on form errors for existing variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.type(await screen.findByLabelText('myVar'), '{{ invalid value');

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByLabelText('myVar')).toHaveAccessibleDescription(
        /value has to be json or a literal/i,
      ),
    );

    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable submit button on form errors for new variables', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '{{ invalid value',
    );

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable value/i),
      ).toHaveAccessibleDescription(/value has to be json or a literal/i),
    );

    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable completion button', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(await screen.findByText(/add variable/i));

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should hide completion button on completed tasks', async () => {
    nodeMockServer.use(
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.variables);
          }

          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {
              status: 400,
            },
          );
        },
      ),
    );

    render(
      <Variables
        task={taskMocks.completedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Variables')).toBeInTheDocument();

    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should complete a task with a truncated variable', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.truncatedVariables);
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
      http.get(
        '/v1/variables/:variableId',
        () => {
          return HttpResponse.json(variableMocks.fullVariable());
        },
        {once: true},
      ),
    );
    const mockOnSubmit = vi.fn();
    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByDisplayValue('"000')).toBeInTheDocument();

    user.click(screen.getByDisplayValue('"000'));

    expect(await screen.findByDisplayValue('"0001"')).toBeInTheDocument();

    await user.clear(screen.getByDisplayValue('"0001"'));
    await user.type(screen.getByLabelText('myVar'), '"newVariableValue"');
    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
    await user.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'myVar',
          value: '"newVariableValue"',
        },
      ]),
    );
  });

  it('should preserve full value', async () => {
    const mockVariable: Variable = {
      id: '1-myVar',
      value: '"1112"',
      previewValue: '"1112"',
      name: 'myVar1',
      isValueTruncated: false,
    };
    const mockNewValue = '"new-value"';
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.truncatedVariables);
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
      http.get<{variableId: string}>(
        '/v1/variables/:variableId',
        ({params}) => {
          switch (params.variableId) {
            case '0-myVar':
              return HttpResponse.json(variableMocks.fullVariable());
            case '1-myVar':
              return HttpResponse.json(
                variableMocks.fullVariable(mockVariable),
              );

            default:
              return HttpResponse.error();
          }
        },
      ),
    );
    const {user} = render(
      <Variables
        task={taskMocks.assignedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByDisplayValue('"000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('"111')).toBeInTheDocument();

    await user.click(screen.getByDisplayValue('"000'));

    const firstVariableValueTextarea =
      await screen.findByDisplayValue('"0001"');
    expect(firstVariableValueTextarea).toBeInTheDocument();
    expect(screen.getByDisplayValue('"111')).toBeInTheDocument();

    await user.clear(firstVariableValueTextarea);
    await user.type(firstVariableValueTextarea, mockNewValue);
    await user.click(screen.getByDisplayValue('"111'));

    expect(
      await screen.findByDisplayValue(mockVariable.value),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockNewValue)).toBeInTheDocument();
  });

  it('should show the preview value of a truncated variable', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.truncatedVariables);
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post<never, VariableSearchRequestBody>(
        '/v1/tasks/:taskId/variables/search',
        async ({request}) => {
          if (isRequestingAllVariables(await request.json())) {
            return HttpResponse.json(variableMocks.truncatedVariables);
          }
          return HttpResponse.json(
            [
              {
                message: 'Invalid variables',
              },
            ],
            {status: 400},
          );
        },
      ),
    );
    const mockOnSubmit = vi.fn();
    const {rerender} = render(
      <Variables
        task={taskMocks.unassignedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('"000...')).toBeInTheDocument();

    rerender(
      <Variables
        task={taskMocks.completedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
    );

    expect(await screen.findByText('"000...')).toBeInTheDocument();
  });

  describe('Duplicate variable validations', () => {
    it('should display error if name is the same with one of the existing variables', async () => {
      nodeMockServer.use(
        http.post<never, VariableSearchRequestBody>(
          '/v1/tasks/:taskId/variables/search',
          async ({request}) => {
            if (isRequestingAllVariables(await request.json())) {
              return HttpResponse.json(variableMocks.variables);
            }
            return HttpResponse.json(
              [
                {
                  message: 'Invalid variables',
                },
              ],
              {status: 400},
            );
          },
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));
      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar');

      vi.runOnlyPendingTimers();
      await waitFor(() =>
        expect(
          screen.getByLabelText(/1st variable value/i),
        ).toHaveAccessibleDescription(/value has to be json or a literal/i),
      );
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should display duplicate name error on last edited variable', async () => {
      nodeMockServer.use(
        http.post(
          '/v1/tasks/:taskId/variables/search',
          () => {
            return HttpResponse.json(variableMocks.variables);
          },
          {once: true},
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));
      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar2');

      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);

      await user.click(screen.getByText(/add variable/i));
      await user.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      await user.type(screen.getByLabelText(/2nd variable name/i), 'foo');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).not.toHaveAccessibleDescription(/name must be unique/i),
      );

      await user.type(screen.getByLabelText(/1st variable name/i), 'foo');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/1st variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );
      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should display error if duplicate name is used and immediately started typing on to the value field', async () => {
      nodeMockServer.use(
        http.post<never, VariableSearchRequestBody>(
          '/v1/tasks/:taskId/variables/search',
          async ({request}) => {
            if (isRequestingAllVariables(await request.json())) {
              return HttpResponse.json(variableMocks.variables);
            }
            return HttpResponse.json(
              [
                {
                  message: 'Invalid variables',
                },
              ],
              {status: 400},
            );
          },
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));

      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/1st variable value/i), '1');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      await user.click(await screen.findByText(/add variable/i));

      await user.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/2nd variable value/i), '2');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should continue to display existing duplicate name error', async () => {
      nodeMockServer.use(
        http.post<never, VariableSearchRequestBody>(
          '/v1/tasks/:taskId/variables/search',
          async ({request}) => {
            if (isRequestingAllVariables(await request.json())) {
              return HttpResponse.json(variableMocks.variables);
            }
            return HttpResponse.json(
              [
                {
                  message: 'Invalid variables',
                },
              ],
              {status: 400},
            );
          },
        ),
      );

      const {user} = render(
        <Variables
          task={taskMocks.assignedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: getWrapper(),
        },
      );

      await user.click(await screen.findByText(/add variable/i));

      await user.type(screen.getByLabelText(/1st variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/1st variable value/i), '1');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      await user.click(screen.getByText(/add variable/i));

      await user.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/2nd variable value/i), '2');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      await user.click(screen.getByText(/add variable/i));

      await user.type(screen.getByLabelText(/3rd variable name/i), 'myVar2');
      await user.type(screen.getByLabelText(/3rd variable value/i), '3');

      vi.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.getByLabelText(/3rd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).toHaveAccessibleDescription(/name must be unique/i);
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });
  });
});
