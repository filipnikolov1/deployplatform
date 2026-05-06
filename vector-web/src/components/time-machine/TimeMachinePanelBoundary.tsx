"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";
import { PanelState } from "./PanelState";

interface Props {
  children: ReactNode;
  panelName: string;
  resetKey?: string | number | null;
}

interface State {
  hasError: boolean;
}

export class TimeMachinePanelBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(`Time Machine panel failed: ${this.props.panelName}`, error, info);
  }

  componentDidUpdate(prevProps: Props) {
    if (this.state.hasError && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <PanelState
          tone="danger"
          title={`${this.props.panelName} unavailable`}
          message="This panel hit a frontend error. The rest of Time Machine is still available."
        />
      );
    }

    return this.props.children;
  }
}
