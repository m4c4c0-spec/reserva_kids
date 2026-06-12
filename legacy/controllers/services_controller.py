from flask import Blueprint, jsonify, request
from app.models.service import Service

services_bp = Blueprint('services', __name__)

@services_bp.route('/services', methods=['GET'])
def get_services():
    services = Service.get_all()
    return jsonify([service.to_dict() for service in services])

@services_bp.route('/services', methods=['POST'])
def create_service():
    data = request.json
    new_service = Service(name=data['name'], description=data['description'])
    new_service.save()
    return jsonify(new_service.to_dict()), 201
