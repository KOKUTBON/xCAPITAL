from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Any, Dict

class FormulaPlugin(ABC):
    """Python-side plugin interface (AI/research tooling)."""

    @property
    @abstractmethod
    def id(self) -> str: ...

    @property
    @abstractmethod
    def name(self) -> str: ...

    @abstractmethod
    def evaluate(self, inputs: Dict[str, Any]) -> Dict[str, Any]: ...
