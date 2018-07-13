import React from 'react';

import {Dropdown} from 'components';

import {DateFilter, VariableFilter, NodeFilter, DurationFilter} from './modals';

import FilterList from './FilterList';
import './Filter.css';

export default class Filter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      newFilterType: null,
      editFilter: null
    };
  }

  openNewFilterModal = type => evt => {
    this.setState({newFilterType: type});
  };

  openEditFilterModal = (...filter) => evt => {
    this.setState({editFilter: filter});
  };

  closeModal = () => {
    this.setState({newFilterType: null, editFilter: null});
  };

  getFilterModal = type => {
    switch (type) {
      case 'date':
        return DateFilter;
      case 'rollingDate':
        return DateFilter;
      case 'variable':
        return VariableFilter;
      case 'processInstanceDuration':
        return DurationFilter;
      case 'executedFlowNodes':
        return NodeFilter;
      default:
        return () => null;
    }
  };

  editFilter = (...newFilters) => {
    const filters = [...this.props.data];

    const {type, data} = this.state.editFilter[0];
    let numberToRemove = 1;
    if (type === 'date' || (type === 'variable' && data.type === 'Date')) {
      numberToRemove = 2;
    }

    const index = filters.indexOf(filters.find(v => this.state.editFilter[0].data === v.data));

    filters.splice(index, numberToRemove, ...newFilters);

    this.props.onChange({filter: filters});
    this.closeModal();
  };

  addFilter = (...newFilters) => {
    let filters = this.props.data;
    filters = this.filterIncompatibleExistingFilters(filters, newFilters[0].type, [
      'date',
      'rollingDate'
    ]);
    filters = this.filterIncompatibleExistingFilters(filters, newFilters[0].type, [
      'runningInstancesOnly',
      'completedInstancesOnly'
    ]);

    this.props.onChange({filter: [...filters, ...newFilters]});
    this.closeModal();
  };

  filterIncompatibleExistingFilters = (filters, newFilterType, uniqueTypes) => {
    if (uniqueTypes.includes(newFilterType)) {
      return filters.filter(({type}) => !uniqueTypes.includes(type));
    }
    return filters;
  };

  deleteFilter = (...oldFilters) => {
    this.props.onChange({
      filter: [...this.props.data.filter(filter => !oldFilters.includes(filter))]
    });
  };

  definitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  };

  processDefinitionIsNotSelected = () => {
    return (
      !this.props.processDefinitionKey ||
      this.props.processDefinitionKey === '' ||
      !this.props.processDefinitionVersion ||
      this.props.processDefinitionVersion === ''
    );
  };

  filterByInstancesOnly = type => evt => {
    this.addFilter({
      type,
      data: null
    });
  };

  render() {
    const FilterModal = this.getFilterModal(this.state.newFilterType);

    const EditFilterModal = this.getFilterModal(
      this.state.editFilter ? this.state.editFilter[0].type : null
    );

    return (
      <div className="Filter">
        <label htmlFor="ControlPanel__filters" className="visually-hidden">
          Filters
        </label>
        <FilterList
          {...this.definitionConfig()}
          openEditFilterModal={this.openEditFilterModal}
          data={this.props.data}
          deleteFilter={this.deleteFilter}
        />
        <Dropdown label="Add Filter" id="ControlPanel__filters" className="Filter__dropdown">
          <Dropdown.Option onClick={this.filterByInstancesOnly('runningInstancesOnly')}>
            Running Instances Only
          </Dropdown.Option>
          <Dropdown.Option onClick={this.filterByInstancesOnly('completedInstancesOnly')}>
            Completed Instances Only
          </Dropdown.Option>
          <Dropdown.Option onClick={this.openNewFilterModal('date')}>Start Date</Dropdown.Option>
          <Dropdown.Option onClick={this.openNewFilterModal('processInstanceDuration')}>
            Duration
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('variable')}
          >
            Variable
          </Dropdown.Option>
          <Dropdown.Option
            disabled={this.processDefinitionIsNotSelected()}
            onClick={this.openNewFilterModal('executedFlowNodes')}
          >
            Flow Node
          </Dropdown.Option>
        </Dropdown>
        <FilterModal
          addFilter={this.addFilter}
          close={this.closeModal}
          xml={this.props.xml}
          {...this.definitionConfig()}
        />
        <EditFilterModal
          addFilter={this.editFilter}
          filterData={this.state.editFilter}
          close={this.closeModal}
          xml={this.props.xml}
          {...this.definitionConfig()}
        />
      </div>
    );
  }
}
