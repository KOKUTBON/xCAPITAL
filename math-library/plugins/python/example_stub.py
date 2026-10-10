from .plugin_base import FormulaPlugin

class ExampleStub(FormulaPlugin):
    @property
    def id(self) -> str:
        return "example_formula"

    @property
    def name(self) -> str:
        return "Example Formula (Stub)"

    def evaluate(self, inputs):
        return {"status": "stub", "message": "Implement me"}
